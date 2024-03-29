package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.calculator.CalculatorException
import il.ac.technion.cs.softwaredesign.calculator.calculate
import il.ac.technion.cs.softwaredesign.exceptions.NameFormatException
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserAlreadyLoggedInException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
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
class CourseBotManager @Inject constructor(private val app : CourseApp, private val messageFactory : MessageFactory,
                                           private var storageFactory : SecureStorageFactory) : CourseBots {

    private lateinit var storage : ScopedStorage
    // names
    private lateinit var allBotsDB : LinkedList
    // name -> token
    private lateinit var allBotsTokensDB : Dictionary

    override fun prepare(): CompletableFuture<Unit> {
        return CompletableFuture.completedFuture(Unit)
    }

    override fun start(): CompletableFuture<Unit> {
        storage = ScopedStorage(CachedStorage(storageFactory.open("bots".toByteArray()).join()), "root")
        allBotsDB = getLinkedList(storage, "All bots")
        allBotsTokensDB = getDict(storage, "All bots tokens")

        return CompletableFuture.runAsync {
            allBotsDB.forEach { name ->

                val token = try {
                    val ret = app.login(name, MASTERPASSWORD).joinException()
                    allBotsTokensDB.write(name, ret)
                    ret
                } catch (e: UserAlreadyLoggedInException) {
                    allBotsTokensDB.read(name) ?: throw AssertionError("Bot already logged in but token not found")
                }



                allBots[name] = CourseBotInstance(token, name)
                allBots[name]!!.initialize().join()
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

            allBotsTokensDB.write(username, token)
            allBotsDB.add(username)
            allBots[username] = bot
            bot.initialize().join()
            bot
        }

    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        val ret = ArrayList<String>()
        allBotsDB.forEach{ // DB is sorted, local copy is a hash map
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

            protected var taskScope : SecureStorage = botScope.scope(name)



            private var taskRootDict : Dictionary = getDict(taskScope, "root")
            protected fun readField(key : String) : String? = taskRootDict.read(key)
            protected fun writeField(key : String, value : String) = taskRootDict.write(key, value)
        }


        private val onMessage : ListenerCallback = {
            source, message ->

            if (!source.contains("@"))
                CompletableFuture.completedFuture(Unit)
            else
            {
                val tasks = ArrayList<CompletableFuture<*>>()
                botEventObservers.forEach {tasks.add(CompletableFuture.runAsync{ it.onMessage(source, message)})}
                CompletableFuture.allOf(*tasks.toTypedArray())
                        .thenApply { Unit }
            }
        }

        fun initialize() : CompletableFuture<Unit> {
            val tasks = ArrayList<CompletableFuture<*>>()
            botEventObservers.forEach {tasks.add(CompletableFuture.runAsync{ it.onInit()}) }
            return CompletableFuture.allOf(*tasks.toTypedArray())
                    .thenCompose { app.addListener(token, onMessage) }
        }


        val channels = getLinkedList(botScope, "channels")

        override fun join(channelName: String): CompletableFuture<Unit> {

            try {
                return app.channelJoin(token, channelName)
                        .exceptionally { throw UserNotAuthorizedException() }
                        .thenApply {
                            channels.add(channelName)

                            val tasks = ArrayList<CompletableFuture<*>>()
                            botEventObservers.forEach {
                                tasks.add(CompletableFuture.runAsync{ it.onChannelJoin(channelName)})
                            }

                            tasks}
                        .thenCompose { CompletableFuture.allOf(*it.toTypedArray()) }
                        .thenApply {  Unit }
            } catch (e: NameFormatException) {
                throw UserNotAuthorizedException()
            }



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

                        tasks}
                    .thenCompose { CompletableFuture.allOf(*it.toTypedArray()) }
                    .thenApply {  Unit }
        }


        override fun channels(): CompletableFuture<List<String>> {
            return CompletableFuture.supplyAsync {
                val list = ArrayList<String>()
                channels.forEach {list.add(it)}
                list
            }
        }

        private inner class MessageCounter : BotEventObserver("MessageCounter") {


            // Channels that have counters. TODO need to know whether channelPart deletes counters or resets them
            private val relevantChannels = getLinkedList(taskScope, "relevantChannels")
            private val allChannelsDBString = "All channels"

            private inner class MessageCountersOfChannel(private val channel: String) {

                init {
                    relevantChannels.add(channel)
                }

                private val channelScope = taskScope.scope(channel)
                private val dict = getDict(channelScope, "filtermap")
                private val set = getLinkedList(channelScope, "filterlist")

                fun new(key : String) {
                    set.add(key)
                    dict.write(key, "0")
                }

                fun set(key : String, amount : Int) = dict.write(key, amount.toString())

                fun load() {
                    localMessageCounters[channel] = HashMap()
                    set.forEach {
                        val key = deserialize(it)
                        val value = dict.read(it)!!
                        localMessageCounters[channel]!![key] = value.toInt()
                    }
                }
                // TODO im resetting the counter and not deleting it.
                fun reset() = set.forEach { dict.write(it, "0") }

                fun checkChannelLocally(message : Message) {
                    val messagestr = message.contents.toString(Charsets.UTF_8)
                    val counter = localMessageCounters[channel] ?: return


                    counter.forEach {
                        val regex = it.key.first
                        val mediatype = it.key.second

                        if (mediatype != null && mediatype != message.media)
                            return@forEach

                        if (regex != null && !Regex(regex).containsMatchIn(messagestr))
                            return@forEach


                        val current = it.value
                        counter[it.key] = current + 1

                        val key = serialize(it.key)

                        MessageCountersOfChannel(channel).set(key, current + 1)
                    }
                }
            }


            private val DELIMITER : String = "\u0001"
            private val EMPTY : String = "\u0002"
            private fun serialize(pair : Pair<String?, MediaType?>) : String {
                return (pair.second?.name ?: EMPTY) + DELIMITER + (pair.first ?: EMPTY)
            }
            private fun deserialize(str : String) : Pair<String?, MediaType?> {
                val split = str.split(DELIMITER)

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


            // (Channel || "All Channels") -> (Regex?, MediaType?) -> (Count)
            val localMessageCounters = HashMap<String, HashMap<Pair<String?, MediaType?>, Int> >()
            override fun onInit() {
                relevantChannels.forEach {chname ->
                    MessageCountersOfChannel(chname).load()
                }
            }

            override fun onChannelPart(channelName: String) {
                MessageCountersOfChannel(channelName).reset()
            }

            override fun onMessage(source: String, message: Message) {
                val channel = getChannelFromChannelMessageSource(source)

                MessageCountersOfChannel(channel).checkChannelLocally(message)
                MessageCountersOfChannel(allChannelsDBString).checkChannelLocally(message)
            }


            fun beginCount(channel: String?,
                           regex: String?,
                           mediaType: MediaType?): CompletableFuture<Unit> {

                return CompletableFuture.supplyAsync {
                    if (regex == null && mediaType == null) throw IllegalArgumentException()
                    if (channel != null && !(getIsInChannel(channel).join())) throw IllegalArgumentException()

                    val channelname = channel ?: allChannelsDBString

                    // add locally
                    localMessageCounters.putIfAbsent(channelname, HashMap())
                    localMessageCounters[channelname]!![Pair(regex, mediaType)] = 0


                    // add remotely
                    relevantChannels.add(channelname)
                    val key = serialize(Pair(regex, mediaType))
                    MessageCountersOfChannel(channelname).new(key)

                    Unit
                }
            }

            fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
                return CompletableFuture.supplyAsync {
                    if (regex == null && mediaType == null) throw IllegalArgumentException()

                    val ret = localMessageCounters[channel?:allChannelsDBString]
                                  ?.get(Pair(regex, mediaType))
                              ?: throw IllegalArgumentException()

                    ret.toLong()
                }
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
            private val calcTriggerDBString = "calcTrigger"


            override fun onInit() {
                calcTrigger = readField(calcTriggerDBString)
                if (calcTrigger == "") calcTrigger = null
            }

            override fun onMessage(source: String, message: Message) {
                val messagestr = message.contents.toString(Charsets.UTF_8)
                if (!messagestr.startsWith((calcTrigger ?: return) + " ")) return
                val res = try {
                    calculate(messagestr.substring(calcTrigger!!.length + 1))
                } catch (e: CalculatorException) {
                    return
                }

                messageFactory.create(MediaType.TEXT, res.toString().toByteArray()).thenApply {
                    app.channelSend(token, getChannelFromChannelMessageSource(source), it)
                }.join()
            }

            fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> =
                    CompletableFuture.supplyAsync {
                        val old = this.calcTrigger
                        this.calcTrigger = trigger
                        writeField(calcTriggerDBString, trigger ?: "")

                        old
                    }
        }

        override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> =
                calculatorComponent.setCalculationTrigger(trigger)


        private inner class Tips : BotEventObserver("Tips") {
            // On DB:
            // root/tipTrigger
            // $channel/heap (username -> money)
            // $channel/set (list of usernames in above heap)

            private inner class TipsOfChannel(channel: String) {
                private val channelScope = taskScope.scope (channel)
                private val heap = getHeap(channelScope, "heap")
                private val set = getLinkedList(channelScope, "set")

                private fun initializeUserIfDoesntExist(user : String) {
                    if (!set.contains(user)) {
                        set.add(user)
                        heap.add(user)
                        heap.changeScore(user, 1000)
                    }
                }
                fun payIfPossible(sender : String, target : String, amount : Int) {
                    if (amount < 0) return // can't steal money.

                    val current = if (set.contains(sender))
                        heap.getScore(sender)
                    else
                        1000

                    // don't initialize if transaction is bound to fail
                    if (current - amount < 0 ) return


                    initializeUserIfDoesntExist(sender)
                    initializeUserIfDoesntExist(target)


                    heap.changeScore(target, amount)
                    heap.changeScore(sender, -amount)
                }
                fun reset() {
                    val users = ArrayList<String>()
                    set.forEach {
                        users.add(it)
                    }

                    // TODO if we care more about performance implement clear on the library's set/heap
                    users.forEach {
                        set.remove(it)
                        heap.remove(it)
                    }
                }
                fun getTop() : String? = getTopIfNoDraw(heap)
            }

            private var tipTrigger : String? = null
            private val tipTriggerDBString = "tipTrigger"

            override fun onInit() {
                tipTrigger = readField(tipTriggerDBString)
                if (tipTrigger == "") tipTrigger = null
            }

            override fun onChannelPart(channelName: String) = TipsOfChannel(channelName).reset()


            override fun onMessage(source: String, message: Message) {
                val data = message.contents.toString(Charsets.UTF_8)
                if (tipTrigger == null || !data.startsWith(tipTrigger!! + " "))
                    return

                val dataPastTrigger = data.substring(tipTrigger!!.length + 1)
                val split = dataPastTrigger.split(" ")
                val amount = split[0].toIntOrNull() ?: return
                val target = split.subList(1, split.size).joinToString(" ")
                val channel = getChannelFromChannelMessageSource(source)
                val sender = getSenderFromChannelMessageSource(source)

                if (app.isUserInChannel(token, channel, target).join() != true) return

                TipsOfChannel(channel).payIfPossible(sender, target, amount)
            }

            fun setTipTrigger(trigger: String?): CompletableFuture<String?> =
                    CompletableFuture.supplyAsync {
                        val old = this.tipTrigger
                        this.tipTrigger = trigger
                        writeField(tipTriggerDBString, trigger ?: "")

                        old
                    }

            fun richestUser(channel: String): CompletableFuture<String?> =
                    CompletableFuture.supplyAsync {
                        if (!getIsInChannel(channel).join()) throw NoSuchEntityException()

                        TipsOfChannel(channel).getTop()
                    }
        }

        override fun setTipTrigger(trigger: String?): CompletableFuture<String?> = tipsComponent.setTipTrigger(trigger)
        override fun richestUser(channel: String): CompletableFuture<String?> = tipsComponent.richestUser(channel)


        private inner class LastSeen : BotEventObserver("LastSeen") {
            // On DB:
            // LastSeen (username -> time)

            override fun onMessage(source: String, message: Message) {
                val sender = getSenderFromChannelMessageSource(source)
                getDict(taskScope, "LastSeen").write(sender, message.created.toString()) // TODO can be serialized better
            }

            fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
                return CompletableFuture.supplyAsync {
                    val lastSeen = getDict(taskScope, "LastSeen").read(user)

                    if (lastSeen == null)
                        null
                    else
                        LocalDateTime.parse(lastSeen)
                }
            }

        }
        override fun seenTime(user: String): CompletableFuture<LocalDateTime?> =
                lastSeenComponent.seenTime(user)


        private inner class ActiveUsers : BotEventObserver("ActiveUsers") {
            // on DB:
            // $channel/heap (username -> count)
            // $channel/set (usernames in above heap)

            private inner class ActiveUsersOfChannel(channel: String) {
                private val channelScope = taskScope.scope(channel)
                private val heap = getHeap(channelScope, "heap")
                private val set = getLinkedList(channelScope, "set")

                private fun addIfDoesntExist(user : String) {
                    if (!set.contains(user)) {
                        set.add(user)
                        heap.add(user)
                    }
                }

                fun incrementCounter(user : String){
                    addIfDoesntExist(user)
                    heap.changeScore(user, 1)
                }

                fun reset() {
                    val users = ArrayList<String>()
                    set.forEach {
                        users.add(it)
                    }

                    // TODO if we care more about performance implement clear on the library's set/heap
                    users.forEach {
                        set.remove(it)
                        heap.remove(it)
                    }
                }

                fun getTop() : String? {
                    return getTopIfNoDraw(heap)
                }
            }


            override fun onChannelPart(channelName: String) {
                ActiveUsersOfChannel(channelName).reset()
            }

            override fun onMessage(source: String, message: Message) {
                val user = getSenderFromChannelMessageSource(source)
                val channel = getChannelFromChannelMessageSource(source)

                ActiveUsersOfChannel(channel).incrementCounter(user)
            }
            fun mostActiveUser(channel: String): CompletableFuture<String?> =
                    getIsInChannel(channel)
                        .thenAccept { if (!it) throw NoSuchEntityException() }
                        .thenApply { ActiveUsersOfChannel(channel).getTop() }
        }

        override fun mostActiveUser(channel: String): CompletableFuture<String?> =
            activeUsersComponent.mostActiveUser(channel)



        private inner class Surveys : BotEventObserver("surveys") {
            // TODO what to reset on channel part?


            // on DB:
            // root/channel (String)
            // root/answers (List<String>)
            // results (answer -> count)
            // userVotes (username -> answer)


            // Local
            private val activeSurvies = HashMap<Int, Survey>()

            var surveyCounter = 0
            val surveyCounterDBString = "surveyCounter"

            val activeSurviesDB = getLinkedList(taskScope, "surveys")

            override fun onInit() {
                surveyCounter = readField(surveyCounterDBString)?.toIntOrNull() ?: 0

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
                private lateinit var channel : String
                private lateinit var answers : List<String>

                val userVotes = getDict(surveyScope, "userVotes")
                val results = getDict(surveyScope, "results")

                val channelString = "channel"
                val answerListString = "answers"

                fun initialize() { // from DB
                    this.channel = rootDict.read(channelString)!!
                    this.answers = rootDict.read(answerListString)!!.split("\u0000")

                }
                fun initialize(channel : String, answers: List<String>) { // new
                    this.channel = channel
                    this.answers = answers

                    rootDict.write(channelString, channel)
                    rootDict.write(answerListString, answers.joinToString("\u0000") )

                    val results = getDict(surveyScope, "results")
                    answers.forEach{
                        results.write(it, "0")
                    }
                }

                fun onMessage(source: String, message: Message) {
                    val channel = getChannelFromChannelMessageSource(source)
                    if (channel != this.channel) return

                    val answer = answers.find { message.contents.toString(Charsets.UTF_8) == it  } ?: return
                    val username = getSenderFromChannelMessageSource(source)

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
                    val ret = ArrayList<Long>()
                    answers.forEach {
                        ret.add(results.read(it)!!.toLong())
                    }
                    return ret
                }
            }

            fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
                if (!getIsInChannel(channel).join()) return CompletableFuture.failedFuture(NoSuchEntityException())

                val uniqueSurveyID = surveyCounter
                surveyCounter += 1
                writeField(surveyCounterDBString, surveyCounter.toString())


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
                return CompletableFuture.supplyAsync {
                    val uniqueID = identifier.toIntOrNull() ?: throw NoSuchEntityException()
                    if (uniqueID >= surveyCounter) throw NoSuchEntityException()


                    activeSurvies[uniqueID]!!.getResults() // important assert - should always exist
                }
            }

        }

        override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> =
                surviesComponent.runSurvey(channel, question, answers)

        override fun surveyResults(identifier: String): CompletableFuture<List<Long>> =
                surviesComponent.surveyResults(identifier)



        // Returns true if bot is in channel, otherwise false. Never throws.
        private fun getIsInChannel(channel : String) : CompletableFuture<Boolean> {
            return CompletableFuture.supplyAsync {
                channels.contains(channel)
            }
        }
    }

    private fun getTopIfNoDraw(heap : MaxHeap) : String? {
        val top10 = heap.topTen()
        if (top10.isEmpty() || top10.size >= 2 && heap.getScore(top10[0]) == heap.getScore(top10[1]))
            return null

        return top10.first()
    }
}

