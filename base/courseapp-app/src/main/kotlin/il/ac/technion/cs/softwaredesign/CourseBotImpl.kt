package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.calculator.calculate
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture


private fun getSenderFromChannelMessageSource(source : String) : String {
    return source.split("@")[1]
}
private fun getChannelFromChannelMessageSource(source : String) : String {
    return source.split("@")[0]
}


private val MASTERPASSWORD = "password"
class CourseBotManager @Inject constructor(val app : CourseApp, val messageFactory : MessageFactory) : CourseBots {


    override fun prepare(): CompletableFuture<Unit> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun start(): CompletableFuture<Unit> {
        TODOallbotsonDB.forEach { name ->
            app.login(name, MASTERPASSWORD)
                    .thenApply { token -> allBots[name] = createExistingBotInstance(name, token)  }
        }

        return CompletableFuture.completedFuture(Unit)
    }

    // TODO an Array of bot and their data and token on the DB
    val TODOallbotsonDB = ArrayList<String>()

    // After start is called, this will have all the bots. (Local, not on DB)
    val allBots = HashMap<String, CourseBot>()


    override fun bot(name: String?): CompletableFuture<CourseBot> {
        val uniqueBotID = allBots.size // This is fine as long as we don't delete bots

        val username = name ?: "Anna$uniqueBotID"


        // Bot exists
        if ( allBots[name] != null)
            return CompletableFuture.completedFuture(allBots[name])

        // New bot (and user for bot)
        return app.login(username, MASTERPASSWORD).thenApply { token ->
            val bot = createNewBotInstance(token, username)

            TODOallbotsonDB.add(username)
            allBots[username] = bot
            bot
        }

    }

    private fun createNewBotInstance(token : String, name : String) : CourseBot =
            CourseBotInstance(token, name, true)
    private fun createExistingBotInstance(token : String, name : String) : CourseBot =
             CourseBotInstance(token, name, false)



    override fun bots(channel: String?): CompletableFuture<List<String>> {
        return TODO()
    }

    // Can't put in inner class?
    private interface BotEventObserver {
        fun onFirstInit() : Unit = Unit
        fun onInit() : Unit = Unit
        fun onMessage(source : String, message : Message) : Unit = Unit
        fun onChannelPart(channelName : String) : Unit = Unit
        fun onChannelJoin(channelName : String) : Unit = Unit
    }

    inner class CourseBotInstance(val token: String, val name: String, isNew : Boolean) : CourseBot{



        private val calculatorComponent = Calculator()
        private val messageCounterComponent  = MessageCounter()
        private val tipsComponent = Tips()
        private val lastSeenComponent = LastSeen()
        private val activeUsersComponent = ActiveUsers()
        private val surviesComponent = Survies()
        private val botEventObservers
                = listOf(messageCounterComponent, calculatorComponent, tipsComponent,
                lastSeenComponent, activeUsersComponent, surviesComponent)

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
                if (isNew) {
                    tasks.add(CompletableFuture.runAsync{ it.onFirstInit(); it.onInit()})
                } else {
                    tasks.add(CompletableFuture.runAsync{ it.onInit()})
                }
            }
            CompletableFuture.allOf(*tasks.toTypedArray())
                    .thenApply { Unit }

            app.addListener(token, onMessage)
        }


        // TODO a Set of channels on the DB: need O(1) add/remove/exists, O(N) getAll
//        val channels = ArrayList<String>()

        override fun join(channelName: String): CompletableFuture<Unit> {
            return app.channelJoin(token, channelName)
                    .exceptionally { throw UserNotAuthorizedException() }
                    .thenApply {
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
                        val tasks = ArrayList<CompletableFuture<*>>()
                        botEventObservers.forEach {
                            tasks.add(CompletableFuture.runAsync{ it.onChannelPart(channelName)})
                        }

                        CompletableFuture.allOf(*tasks.toTypedArray())
                    }.thenApply {  Unit }
        }


        override fun channels(): CompletableFuture<List<String>> {
            return TODO()
            //return CompletableFuture.completedFuture(channels)
        }

        ///////////////// Message counter

        private inner class MessageCounter : BotEventObserver {
            override fun onInit() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            // (Channel) -> (Regex?, MediaType?) -> (Count)
            val messageCounters = HashMap<String?, HashMap<Pair<String?, MediaType?>, Int> >()
            override fun onMessage(source: String, message: Message) {
                val channel = getChannelFromChannelMessageSource(source)
                val messagestr = message.contents.toString(Charsets.UTF_8)

                val channelDict = messageCounters[channel] ?: return


                channelDict.forEach {
                    val regex = it.key.first
                    val mediatype = it.key.second

                    if (mediatype != null && mediatype != message.media)
                        return@forEach

                    if (regex != null && !kotlin.text.Regex.fromLiteral(regex).matches(messagestr))
                        return@forEach

                    // TODO increase value
                }
            }

            override fun onChannelPart(channelName: String) {

            }


            fun beginCount(channel: String?,
                                    regex: String?,
                                    mediaType: MediaType?): CompletableFuture<Unit> {

                if (channel == null && regex == null && mediaType == null) throw IllegalArgumentException()

                if (messageCounters[channel] == null)
                    messageCounters[channel] = HashMap()

                // add as key
                messageCounters[channel]!![Pair(regex, mediaType)] = 0

                return CompletableFuture.completedFuture(Unit)
            }

            fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
                if (channel == null && regex == null && mediaType == null) throw IllegalArgumentException()

                val ret = messageCounters[channel]?.get(Pair(regex, mediaType)) ?: 0
                return CompletableFuture.completedFuture(ret.toLong())
            }



        }
        override fun beginCount(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Unit> =
                messageCounterComponent.beginCount(channel, regex, mediaType)

        override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long>  =
                messageCounterComponent.count(channel, regex, mediaType)




        //////////////////// Calc
        // TODO Save to DB
        private inner class Calculator : BotEventObserver {
            private var calcTrigger : String? = null


            override fun onMessage(source: String, message: Message) {
                val messagestr = message.contents.toString(Charsets.UTF_8)
                if (!messagestr.startsWith(calcTrigger ?: return)) {
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
                return CompletableFuture.completedFuture(old)
            }

        }

        override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> =
                calculatorComponent.setCalculationTrigger(trigger)




        private inner class Tips : BotEventObserver {
            /////////////////// Tips
            // TODO Save to DB
            var tipTrigger : String? = null

            // Channel -> Username -> Money
            // TODO Heap
            val tips = HashMap<String, Int>() // TODO don't forget channel

            override fun onMessage(source: String, message: Message) {
                val data = message.contents.toString(Charsets.UTF_8)
                if (tipTrigger == null || !data.startsWith(tipTrigger!!))
                    return

                val dataPastTrigger = data.substring(tipTrigger!!.length + 1)
                val split = dataPastTrigger.split(" ")
                val amount = split[0].toIntOrNull() ?: return
                val target = split.subList(1, split.size).joinToString(" ")
                val sender = getSenderFromChannelMessageSource(source)



                // TODO handle negative
                // Remove from sender
                tips[sender] = (tips[sender] ?: 0) - amount

                // Add to target
                tips[target] = (tips[target] ?: 0) + amount
            }


            fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
                val old = this.tipTrigger
                this.tipTrigger = trigger
                return CompletableFuture.completedFuture(old)
            }


            fun richestUser(channel: String): CompletableFuture<String?> {
                TODO("not implemented") // just return heap's biggest
            }

        }
        override fun setTipTrigger(trigger: String?): CompletableFuture<String?> = tipsComponent.setTipTrigger(trigger)
        override fun richestUser(channel: String): CompletableFuture<String?> = tipsComponent.richestUser(channel)



        private inner class LastSeen : BotEventObserver {
            /////////////////// Last Seen
            // Username ->  Time
            // TODO simple map on the DB
            var seenTime = HashMap<String, LocalDateTime>()
            override fun onMessage(source: String, message: Message) {
                val sender = getSenderFromChannelMessageSource(source)
                seenTime[sender] = message.created
            }

            fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
                val seen = seenTime[user] // Null if user not found
                return CompletableFuture.completedFuture(seen)
            }

        }
        override fun seenTime(user: String): CompletableFuture<LocalDateTime?> =
                lastSeenComponent.seenTime(user)



        private inner class ActiveUsers : BotEventObserver {
            ////////////// Most messages
            // Channel Name -> (Username -> Message count)
            // TODO heap
            val activeUserCounter = HashMap<String, HashMap<String, Int>>()
            override fun onMessage(source: String, message: Message) {
                val user = getSenderFromChannelMessageSource(source)
                val channel = getChannelFromChannelMessageSource(source)


                if (activeUserCounter[channel] == null)
                    activeUserCounter[channel] = HashMap()


                activeUserCounter[channel]!![user] = (activeUserCounter[channel]!![user]?: 0) + 1
            }
            fun mostActiveUser(channel: String): CompletableFuture<String?> {
                TODO("not implemented") // just return heap maximum
            }
        }

        override fun mostActiveUser(channel: String): CompletableFuture<String?> =
            activeUsersComponent.mostActiveUser(channel)




        private inner class Survies : BotEventObserver {
            /////////////////// Survey
            // TODO Array on the DB of String to a "folder" for a active survey object
            private val activeSurvies = HashMap<Int, Survey>()
            override fun onMessage(source: String, message: Message) {
                activeSurvies.forEach {
                    it.value.onMessage(source, message)
                }
            }
            // Handles an ongoing survey
            private inner class Survey(var channel : String, val answers: List<String>, val UniqueSurveyID: Int) {

                // Answer -> Vote count
                // TODO Heap
                val results = HashMap<String, Int>()

                // User name -> Answer
                // TODO simple map
                val votes = HashMap<String, String>()
                init {
                    answers.forEach {
                        results[it] = 0
                    }
                }

                fun onMessage(source: String, message: Message) {
                    if (getChannelFromChannelMessageSource(source) != channel)
                        return

                    val answer = results.keys.find { message.contents.toString(Charsets.UTF_8).contains(it)  } ?: return
                    val username = getSenderFromChannelMessageSource(source)

                    // Remove old vote
                    if (votes.containsKey(username)) {
                        val oldanswer = votes[username]!!
                        results[oldanswer] = results[oldanswer]!! - 1
                    }

                    // Add new vote
                    votes[username] = answer
                    results[answer] = results[answer]!! + 1
                }

                fun getResults() : List<Long> {
                    val res = ArrayList<Long>()
                    answers.forEach {
                        res.add(results[it]!!.toLong())
                    }
                    return res
                }
            }
            var TODO_surveycounter = 0
            fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
                // Throw if bot not in channel
                if (getIsInChannel(channel).join()) throw NoSuchEntityException()

                // TODO unique identifier
                val uniqueSurveyID = TODO_surveycounter
                TODO_surveycounter += 1
                activeSurvies[uniqueSurveyID] = (Survey(channel, answers, uniqueSurveyID))

                return messageFactory.create(MediaType.TEXT, question.toByteArray()).thenApply {
                    app.channelSend(token, channel, it)
                }.thenApply {
                    uniqueSurveyID.toString()
                }
            }

            fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
                val uniqueID = identifier.toIntOrNull() ?: throw NoSuchEntityException()

                val results = (activeSurvies[uniqueID] ?: throw NoSuchEntityException()).getResults()
                activeSurvies.remove(uniqueID)
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


