package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.calculator.calculate
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.extensions.toByteArray
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap


// TODO handle statistics resets on channel part

private fun getSenderFromChannelMessageSource(source : String) : String {
    return source.split("@")[1]
}
private fun getChannelFromChannelMessageSource(source : String) : String {
    return source.split("@")[0]
}



private class ScopedStorage(val storage: SecureStorage, private val scope : List<String>) : SecureStorage {
    constructor(storage: SecureStorage, key : String) : this(storage, listOf(key))


    override fun read(key: ByteArray): CompletableFuture<ByteArray?> {
        return storage.read(scope.joinToString("\u0000").toByteArray() + "\u0000".toByteArray() + key)
    }

    override fun write(key: ByteArray, value: ByteArray): CompletableFuture<Unit> {
        return storage.write(scope.joinToString("\u0000").toByteArray() + "\u0000".toByteArray() +  key, value)
    }
    fun scope(key: String) : ScopedStorage {
        return ScopedStorage(storage, scope + key)
    }
}
fun SecureStorage.scope(key : String) : SecureStorage = ScopedStorage(this, listOf(key))
fun SecureStorage.scope(key : List<String>) : SecureStorage = ScopedStorage(this, key)

private fun getLinkedList(storage: SecureStorage, name: String) =
        LinkedListImpl(storage.scope(name), intToByteArray(0) + intToByteArray(1) + intToByteArray(2))
private fun getHeap(storage: SecureStorage, name: String) =
        MaxHeapImpl(storage.scope(name), intToByteArray(0))
private fun getDict(storage: SecureStorage, name: String) =
        DictionaryImpl(storage.scope(name), intToByteArray(0))



private val MASTERPASSWORD = "password"
class CourseBotManager @Inject constructor(val app : CourseApp, val messageFactory : MessageFactory) : CourseBots {

    @Inject
    private lateinit var storageFactory : SecureStorageFactory
    private lateinit var storage : ScopedStorage


    override fun prepare(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit)
    }

    override fun start(): CompletableFuture<Unit> {
        storage = ScopedStorage(storageFactory.open("bots".toByteArray()).join(), "root")

        return CompletableFuture.runAsync {
            getLinkedList(storage, "All bots").forEach { name ->
                app.login(name, MASTERPASSWORD)
                        .thenApply { token -> allBots[name] = CourseBotInstance(name, token)  }
            }
        }.thenApply { Unit }
    }



    // After start is called, this will have all the bots. (Local, not on DB)
    val allBots = HashMap<String, CourseBotInstance>()

    override fun bot(name: String?): CompletableFuture<CourseBot> {
        val uniqueBotID = allBots.size // This is fine as long as we don't delete bots

        val username = name ?: "Anna$uniqueBotID"


        // Bot exists
        if ( allBots[name] != null)
            return CompletableFuture.completedFuture(allBots[name])

        // New bot (and user for bot)
        return app.login(username, MASTERPASSWORD).thenApply { token ->
            val bot = CourseBotInstance(token, username)

            getLinkedList(storage, "All bots").add(username)
            allBots[username] = bot
            bot
        }

    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        val sortedbotlist = getLinkedList(storage, "All bots")

        val ret = ArrayList<String>()
        sortedbotlist.forEach{
            val bot = allBots[it]!!
            if (channel == null || bot.channels.contains(channel))
                ret.add(it)

        }
        return CompletableFuture.completedFuture(ret)
    }

    inner class CourseBotInstance(val token: String, val name: String) : CourseBot{

        private val botScope :SecureStorage =
                storage.scope("bots").scope(name)

        private val calculatorComponent = Calculator()
        private val messageCounterComponent  = MessageCounter()
        private val tipsComponent = Tips()
        private val lastSeenComponent = LastSeen()
        private val activeUsersComponent = ActiveUsers()
        private val surviesComponent = Surveys()
        private val botEventObservers
                = listOf(messageCounterComponent, calculatorComponent, tipsComponent,
                lastSeenComponent, activeUsersComponent, surviesComponent)




        private abstract inner class BotEventObserver(name: String) {
            open fun onInit() : Unit = Unit
            open fun onMessage(source : String, message : Message) : Unit = Unit
            open fun onChannelPart(channelName : String) : Unit = Unit
            open fun onChannelJoin(channelName : String) : Unit = Unit
            var taskScope : SecureStorage = botScope.scope(name)
            var taskRootDict : Dictionary = getDict(taskScope, "root")
        }


        private val onMessage : ListenerCallback = {
            source, message ->

            val tasks = ArrayList<CompletableFuture<*>>()
            botEventObservers.forEach {
                tasks.add(CompletableFuture.runAsync{ it.onMessage(source, message)})
            }

            CompletableFuture.allOf(*tasks.toTypedArray())
                    .thenApply { Unit }
        }

        init {
            val tasks = ArrayList<CompletableFuture<*>>()
            botEventObservers.forEach {
                    tasks.add(CompletableFuture.runAsync{ it.onInit()})
            }
            CompletableFuture.allOf(*tasks.toTypedArray())
                    .thenApply { Unit }

            app.addListener(token, onMessage)
        }


        val channels = getLinkedList(botScope, "channels")

        override fun join(channelName: String): CompletableFuture<Unit> {
            return app.channelJoin(token, channelName)
                    .exceptionally { throw UserNotAuthorizedException() }
                    .thenApply {
                        channels.add(channelName)

                        val tasks = ArrayList<CompletableFuture<*>>()
                        botEventObservers.forEach {
                            tasks.add(CompletableFuture.runAsync{ it.onChannelJoin(channelName)})
                        }

                        CompletableFuture.allOf(*tasks.toTypedArray())
                    }.thenApply {  Unit }
        }

        override fun part(channelName: String): CompletableFuture<Unit> {
            return app.channelPart(token, channelName)
                    .exceptionally { throw NoSuchEntityException() }
                    .thenApply {
                        channels.remove(channelName)


                        val tasks = ArrayList<CompletableFuture<*>>()
                        botEventObservers.forEach {
                            tasks.add(CompletableFuture.runAsync{ it.onChannelPart(channelName)})
                        }

                        CompletableFuture.allOf(*tasks.toTypedArray())
                    }.thenApply {  Unit }
        }


        override fun channels(): CompletableFuture<List<String>> {
            return CompletableFuture.supplyAsync {
                val list = ArrayList<String>()
                channels.forEach {list.add(it)}
                list
            }
        }

        private inner class MessageCounter : BotEventObserver("MessageCounter") {

            private val EMPTY : String = "\u0001"
            private fun serialize(pair : Pair<String?, MediaType?>) : String {
                return (pair.second?.name ?: EMPTY) + '\u0000' + (pair.first ?: EMPTY)
            }
            private fun deserialize(str : String) : Pair<String?, MediaType?> {
                val split = str.split("\u0000")

                val type : MediaType?
                if (split[0] == EMPTY)
                    type = null
                else
                    type = MediaType.valueOf(split[0])

                val regex : String?
                if (split[1] == EMPTY)
                    regex = null
                else
                    regex = split[1]


                return Pair(regex,type)
            }



            // (Channel) -> (Regex?, MediaType?) -> (Count)
            val localMessageCounters = HashMap<String, HashMap<Pair<String?, MediaType?>, Int> >()
            override fun onInit() {
                getLinkedList(taskScope, "relevantChannels").forEach {chname ->
                    localMessageCounters[chname] = HashMap()
                    val channelScope = taskScope.scope(chname)
                    val dict = getDict(channelScope, "filtermap")
                    val set = getLinkedList(channelScope, "filterlist")
                    set.forEach {
                        val key = deserialize(it)
                        val value = dict.read(it)!!
                        localMessageCounters[chname]!![key] = value.toInt()
                    }
                }
            }


            override fun onMessage(source: String, message: Message) {
                val channel = getChannelFromChannelMessageSource(source)

                val channelScope = taskScope.scope(channel)
                val dict = getDict(channelScope, "filtermap")

                val messagestr = message.contents.toString(Charsets.UTF_8)

                val counter = localMessageCounters[channel] ?: return
                counter.forEach {
                    val regex = it.key.first
                    val mediatype = it.key.second

                    if (mediatype != null && mediatype != message.media)
                        return@forEach

                    if (regex != null && !kotlin.text.Regex.fromLiteral(regex).matches(messagestr))
                        return@forEach


                    val current = it.value
                    counter[it.key] = current + 1



                    val key = serialize(it.key)
                    dict.write(key, (current + 1).toString())
                }
            }


            fun beginCount(channel: String?,
                                    regex: String?,
                                    mediaType: MediaType?): CompletableFuture<Unit> {

                if (regex == null && mediaType == null) throw IllegalArgumentException()

                if (channel != null && app.isUserInChannel(token, channel, name).join() != true) throw TODO()

                val channelname = channel ?: "All channels"

                // add locally
                if (localMessageCounters[channelname] == null)
                    localMessageCounters[channelname] = HashMap()
                localMessageCounters[channelname]!![Pair(regex, mediaType)] = 0


                // add remotely
                val channelScope = taskScope.scope(channelname)
                val dict = getDict(channelScope, "filtermap")
                val set = getLinkedList(channelScope, "filterlist")
                getLinkedList(taskScope, "relevantChannels").add(channelname)


                val key = serialize(Pair(regex, mediaType))
                return CompletableFuture.runAsync{
                    set.add(key)
                    dict.write(key, "0")
                }.thenApply { Unit }

            }

            fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
                if (channel == null && regex == null && mediaType == null) throw IllegalArgumentException()

                val ret = localMessageCounters[channel]?.get(Pair(regex, mediaType)) ?: throw IllegalArgumentException()
                return CompletableFuture.completedFuture(ret.toLong())
            }



        }
        override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> =
                messageCounterComponent.beginCount(channel, regex, mediaType)

        override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long>  =
                messageCounterComponent.count(channel, regex, mediaType)


        private inner class Calculator : BotEventObserver( "Calculator") {
            // On DB:
            // root/calcTrigger

            private var calcTrigger : String? = null

            override fun onInit() {
                calcTrigger = taskRootDict.read("calcTrigger")
                if (calcTrigger == "") calcTrigger = null
            }

            override fun onMessage(source: String, message: Message) {
                val messagestr = message.contents.toString(Charsets.UTF_8)
                if (!messagestr.startsWith((calcTrigger ?: return) + " ")) {
                    return
                }
                val res = calculate(messagestr.substring(calcTrigger!!.length + 1))

                messageFactory.create(MediaType.TEXT, res.toString().toByteArray()).thenApply {
                    app.channelSend(token, getChannelFromChannelMessageSource(source), it)
                }.join()
            }

            fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
                val old = this.calcTrigger
                this.calcTrigger = trigger
                taskRootDict.write("calcTrigger", trigger ?: "")
                return CompletableFuture.completedFuture(old)
            }

        }

        override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> =
                calculatorComponent.setCalculationTrigger(trigger)




        private inner class Tips : BotEventObserver("Tips") {
            // On DB:
            // root/tipTrigger
            // $channel/heap (username -> money)
            // $channel/set (list of usernames in above heap)

            var tipTrigger : String? = null


            override fun onInit() {
                tipTrigger = taskRootDict.read("tipTrigger")
                if (tipTrigger == "") tipTrigger = null
            }

            override fun onMessage(source: String, message: Message) {
                val data = message.contents.toString(Charsets.UTF_8)
                if (tipTrigger == null || !data.startsWith(tipTrigger!!))
                    return

                val dataPastTrigger = data.substring(tipTrigger!!.length + 1)
                val split = dataPastTrigger.split(" ")
                val amount = split[0].toIntOrNull() ?: return
                val target = split.subList(1, split.size).joinToString(" ")
                val channel = getChannelFromChannelMessageSource(source)
                val sender = getSenderFromChannelMessageSource(source)


                val channelScope = taskScope.scope (channel)
                val heap = getHeap(channelScope, "heap")
                val set = getLinkedList(channelScope, "set")

                if (!set.contains(sender)) {
                    set.add(sender)
                    heap.add(sender)
                    heap.changeScore(sender, 1000)
                }

                if (!set.contains(target)) {
                    set.add(target)
                    heap.add(target)
                    heap.changeScore(target, 1000)
                }

                if (heap.getScore(sender) - amount < 0 ) return

                heap.changeScore(target, amount)
                heap.changeScore(sender, -amount)
            }


            fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
                val old = this.tipTrigger
                this.tipTrigger = trigger
                taskRootDict.write("tipTrigger", trigger ?: "")
                return CompletableFuture.completedFuture(old)
            }


            fun richestUser(channel: String): CompletableFuture<String?> {
                val channelScope = taskScope.scope(channel)
                val heap = getHeap(channelScope, "heap")
                return CompletableFuture.completedFuture(heap.topTen()[0])
            }

        }
        override fun setTipTrigger(trigger: String?): CompletableFuture<String?> = tipsComponent.setTipTrigger(trigger)
        override fun richestUser(channel: String): CompletableFuture<String?> = tipsComponent.richestUser(channel)



        private inner class LastSeen : BotEventObserver("LastSeen") {
            // On DB:
            // LastSeen (username -> time)

            override fun onMessage(source: String, message: Message) {
                val sender = getSenderFromChannelMessageSource(source)
                val fromEpoch = message.created.toEpochSecond(ZoneOffset.UTC)
                getDict(taskScope, "LastSeen").write(sender, fromEpoch.toString()) // TODO can be serialized
            }

            fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
                val lastSeen = getDict(taskScope, "LastSeen").read(user)?.toIntOrNull()
                        ?: return CompletableFuture.completedFuture(null)

                return CompletableFuture.completedFuture(LocalDateTime.ofEpochSecond(lastSeen.toLong(), 0, ZoneOffset.UTC))
            }

        }
        override fun seenTime(user: String): CompletableFuture<LocalDateTime?> =
                lastSeenComponent.seenTime(user)



        private inner class ActiveUsers : BotEventObserver("ActiveUsers") {
            // on DB:
            // $channel/heap (username -> count)
            // $channel/set (usernames in above heap)

            override fun onMessage(source: String, message: Message) {
                val user = getSenderFromChannelMessageSource(source)
                val channel = getChannelFromChannelMessageSource(source)

                val channelScope = taskScope.scope(channel)
                val heap = getHeap(channelScope, "heap")
                val set = getLinkedList(channelScope, "set")
                if (!set.contains(user)) {
                    set.add(user)
                    heap.add(user)
                }

                heap.changeScore(user, 1)
            }
            fun mostActiveUser(channel: String): CompletableFuture<String?> {
                val channelScope = taskScope.scope(channel)
                val heap = getHeap(channelScope, "heap")
                if (heap.isEmpty()) return CompletableFuture.completedFuture(null)

                return CompletableFuture.completedFuture(heap.topTen()[0])
            }
        }

        override fun mostActiveUser(channel: String): CompletableFuture<String?> =
            activeUsersComponent.mostActiveUser(channel)




        private inner class Surveys : BotEventObserver("surveys") {
            // on DB:
            // root/channel (String)
            // root/answers (List<String>)
            // results (answer -> count)
            // userVotes (username -> answer)


            // Local
            private val activeSurvies = HashMap<Int, Survey>()

            var surveyCounter = 0
            override fun onInit() {
                surveyCounter = taskRootDict.read("surveyCounter")?.toIntOrNull() ?: 0

                val activeSurviesDB = getLinkedList(taskScope, "surveys")
                activeSurviesDB.forEach {
                    val id = it.toIntOrNull()!!
                    activeSurvies[id] = Survey(taskScope.scope("Survey $id"))
                    activeSurvies[id]!!.initialize()
                }


            }

            override fun onMessage(source: String, message: Message) {
                activeSurvies.forEach {
                    it.value.onMessage(source, message)
                }
            }
            // Handles a single survey
            private inner class Survey(val surveyScope: SecureStorage) {
                // on DB:
                // root/channel (String)
                // root/answers (List<String>)
                // results (answer -> count)
                // userVotes (username -> answer)

                private val rootDict = getDict(surveyScope, "rootDict")
                lateinit var channel : String
                lateinit var answers : List<String>



                fun initialize() { // from DB
                    this.channel = rootDict.read("channels")!!
                    this.answers = rootDict.read("answers")!!.split("\u0000")

                }
                fun initialize(channel : String, answers: List<String>) { // new
                    this.channel = channel
                    this.answers = answers

                    rootDict.write("channel", channel)
                    rootDict.write("answers", answers.joinToString("\u0000") )

                    val results = getDict(surveyScope, "results")
                    answers.forEach{
                        results.write(it, "0")
                    }


                }

                fun onMessage(source: String, message: Message) {
                    val channel = getChannelFromChannelMessageSource(source)
                    if (channel != this.channel) return

                    val answer = answers.find { message.contents.toString(Charsets.UTF_8).contains(it)  } ?: return
                    val username = getSenderFromChannelMessageSource(source)

                    val userVotes = getDict(surveyScope, "userVotes")
                    val results = getDict(surveyScope, "results")

                    // Remove old vote
                    if (userVotes.contains(username)) {
                        val oldanswer = userVotes.read(username)!!
                        results.write(oldanswer, (results.read(oldanswer)!!.toInt() - 1).toString())
                    }

                    // Add new vote
                    userVotes.write(username, answer)
                    results.write(answer, (results.read(answer)!!.toInt() + 1).toString())
                }

                fun getResults() : List<Long> {
                    val results = getDict(surveyScope, "results")
                    val ret = ArrayList<Long>()
                    answers.forEach {
                        ret.add(results.read(it)!!.toLong())
                    }
                    return ret
                }
            }

            fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
                // Throw if bot not in channel
                if (getIsInChannel(channel).join()) throw NoSuchEntityException()

                val uniqueSurveyID = surveyCounter
                surveyCounter += 1
                taskRootDict.write("surveyCounter", surveyCounter.toString())


                val activeSurviesDB = getLinkedList(taskScope, "surveys")
                activeSurviesDB.add(surveyCounter.toString())
                activeSurvies[uniqueSurveyID] = Survey(taskScope.scope("Survey $uniqueSurveyID"))
                activeSurvies[uniqueSurveyID]!!.initialize(channel, answers)

                    return messageFactory.create(MediaType.TEXT, question.toByteArray()).thenApply {
                    app.channelSend(token, channel, it)
                }.thenApply {
                    uniqueSurveyID.toString()
                }
            }

            fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
                val uniqueID = identifier.toIntOrNull() ?: throw NoSuchEntityException()
                if (uniqueID >= surveyCounter) throw NoSuchEntityException()
                val results = (activeSurvies[uniqueID]!!).getResults() // important assert - should always exist
                return CompletableFuture.completedFuture(results)
            }

        }

        override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> =
                surviesComponent.runSurvey(channel, question, answers)

        override fun surveyResults(identifier: String): CompletableFuture<List<Long>> =
                surviesComponent.surveyResults(identifier)



        // Returns true if bot is in channel, otherwise false. Never throws.
        private fun getIsInChannel(channel : String) : CompletableFuture<Boolean> {
            return app.isUserInChannel(token, channel, name)
                    .exceptionally { false }
                    .thenApply { it == true } // false and null will return false

        }
    }

}


