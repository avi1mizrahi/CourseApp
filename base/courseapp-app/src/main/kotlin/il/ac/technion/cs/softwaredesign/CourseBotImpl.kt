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

    // TODO an Array of bot and their data and token on the DB

    // Bots that are locally known and are logged in at this moment
    val activeBots = ArrayList<CourseBotInstance>()
    override fun bot(name: String?): CompletableFuture<CourseBot> {
        val uniqueBotID = 0 // TODO


        if (name != null) {
            val ret = activeBots.find { it.name == name }
            if (ret != null) return CompletableFuture.completedFuture(ret)
        }


        val username = name ?: "Anna$uniqueBotID"
        return app.login(username, MASTERPASSWORD).thenApply { token ->
            val bot = CourseBotInstance()
            bot.initialize(token, username)
            activeBots.add(bot)


            bot
        }

    }



    override fun bots(channel: String?): CompletableFuture<List<String>> {
        return TODO()
    }

    inner class CourseBotInstance : CourseBot{

        // TODO What to serialize and keep up to date on the DB at all times and what to cache?

        lateinit var token : String
        lateinit var name : String
        private val onMessage : ListenerCallback = {
            source, message ->

            CompletableFuture.allOf(
                CompletableFuture.runAsync{handleMessageForCounting(source, message)},
                CompletableFuture.runAsync{handleMessageForSurvies(source, message)},
                CompletableFuture.runAsync{handleMessageForCalc(source, message)},
                CompletableFuture.runAsync{handleMessageForTip(source, message)},
                CompletableFuture.runAsync{handleMessageLastSeen(source, message)},
                CompletableFuture.runAsync{handleMessageForMostActive(source, message)}
            ).thenApply { Unit }
        }


        fun initialize(token : String, name : String) {
            this.name = name
            this.token = token
            app.addListener(token, onMessage)
        }


        // TODO Save things that are not being saved in real time
        private fun saveToDB() {

        }

        fun destroy(){
            app.removeListener(token, onMessage)
            saveToDB()
        }


        // TODO a Set of channels on the DB: need O(1) add/remove/exists, O(N) getAll
        val channels = ArrayList<String>()

        // TODO Reset:
        // messageCounters
        private fun resetStatisticsForChannel(channelName: String) {
            messageCounters.forEach {
                if (it.value[channelName] != null)
                    it.value.remove(channelName)
            }
        }
        private fun addChannel(channelName: String) {
            channels.add(channelName)
        }
        private fun removeChannel(channelName: String) {
            resetStatisticsForChannel(channelName)
            channels.remove(channelName)
        }


        override fun join(channelName: String): CompletableFuture<Unit> {
            return app.channelJoin(token, channelName)
                    .exceptionally { throw UserNotAuthorizedException() }
                    .thenApply { addChannel(channelName) }
        }

        override fun part(channelName: String): CompletableFuture<Unit> {
            return app.channelPart(token, channelName)
                    .exceptionally { throw NoSuchEntityException() }
                    .thenApply {removeChannel(channelName)}
        }

        override fun channels(): CompletableFuture<List<String>> {
            return CompletableFuture.completedFuture(channels)
        }


        ///////////////// Message counter

        // (Regex?, MediaType?) -> (Channel) -> (Count)
        // TODO a map of maps on the DB. Pair<String?, MediaType?> can be a folder containing channels
        // MediaTypeEnum_SomeRegex/ChannelName -> Count
        val messageCounters = HashMap<Pair<String?, MediaType?>, HashMap<String, Int>>()

        private fun handleMessageForCounting(source: String, message: Message) {
            val channel = getChannelFromChannelMessageSource(source)
            val messagestr = message.contents.toString(Charsets.UTF_8)
            messageCounters.forEach {
                val regex = it.key.first
                val mediatype = it.key.second

                if (mediatype != null && mediatype != message.media)
                    return@forEach

                if (regex != null && !kotlin.text.Regex.fromLiteral(regex).matches(messagestr))
                    return@forEach

                it.value[channel] = (it.value[channel] ?: 0) + 1
            }
        }

        override fun beginCount(regex: String?, mediaType: MediaType?): CompletableFuture<Unit> {
            if (regex == null && mediaType == null) throw IllegalArgumentException()

            messageCounters[Pair(regex, mediaType)] = HashMap()
            return CompletableFuture.completedFuture(Unit)
        }
        override fun count(channel: String?, regex: String?, mediaType: MediaType?): CompletableFuture<Long> {
            if (regex == null && mediaType == null) throw IllegalArgumentException()
            val ret = messageCounters[Pair(regex, mediaType)]?.get(channel) ?: 0
            return CompletableFuture.completedFuture(ret.toLong())
        }

        //////////////////// Calc
        // TODO Save to DB
        var calcTrigger : String? = null
        private fun handleMessageForCalc(source: String, message: Message) {
            val messagestr = message.contents.toString(Charsets.UTF_8)
            if (!messagestr.startsWith(calcTrigger ?: return)) {
                return
            }
            val res = calculate(messagestr.substring(calcTrigger!!.length + 1))

            messageFactory.create(MediaType.TEXT, res.toString().toByteArray()).thenApply {
                app.channelSend(token, getChannelFromChannelMessageSource(source), it)
            }.join()

        }

        override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
            val old = this.calcTrigger
            this.calcTrigger = trigger
            return CompletableFuture.completedFuture(old)
        }


        /////////////////// Tips
        // TODO Save to DB
        var tipTrigger : String? = null
        // Username -> Money
        // TODO Heap
        val tips = HashMap<String, Int>()
        private fun handleMessageForTip(source: String, message: Message) {
            val data = message.contents.toString(Charsets.UTF_8)
            if (this.tipTrigger == null || !data.startsWith(this.tipTrigger!!))
                return


            val split = data.split(" ")
            val amount = split[1].toIntOrNull() ?: return
            val target = split.subList(2, split.size).joinToString(" ")
            val sender = getSenderFromChannelMessageSource(source)



            // TODO what to do with negative?
            // Remove from sender
            tips[sender] = (tips[sender] ?: 0) - amount

            // Add to target
            tips[target] = (tips[target] ?: 0) + amount
        }


        override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
            val old = this.tipTrigger
            this.tipTrigger = trigger
            return CompletableFuture.completedFuture(old)
        }


        override fun richestUser(channel: String): CompletableFuture<String?> {
            TODO("not implemented") // just return heap's biggest
        }


        /////////////////// Last Seen
        // Username ->  Time
        // TODO simple map on the DB
        var seenTime = HashMap<String, LocalDateTime>()
        private fun handleMessageLastSeen(source: String, message: Message) {
            val sender = getSenderFromChannelMessageSource(source)
            seenTime[sender] = message.created
        }
        override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
            val seen = seenTime[user] // Null if user not found
            return CompletableFuture.completedFuture(seen)
        }



        ////////////// Most messages

        // Channel Name -> (Username -> Message count)
        // TODO heap
        val activeUserCounter = HashMap<String, HashMap<String, Int>>()
        private fun handleMessageForMostActive(source: String, message: Message) {
            val user = getSenderFromChannelMessageSource(source)
            val channel = getChannelFromChannelMessageSource(source)


            if (activeUserCounter[channel] == null)
                activeUserCounter[channel] = HashMap()


           activeUserCounter[channel]!![user] = (activeUserCounter[channel]!![user]?: 0) + 1
        }
        override fun mostActiveUser(channel: String): CompletableFuture<String?> {
            TODO("not implemented") // just return heap maximum
        }




        /////////////////// Survey
        // TODO Array on the DB of String to a "folder" for a active survey object
        private val activeSurvies = HashMap<Int, Survey>()
        private fun handleMessageForSurvies(source: String, message: Message) {
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
        override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
            // Throw if channel does not exist
            if (channels.find { it == channel } == null) throw NoSuchEntityException()

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

        override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
            val uniqueID = identifier.toIntOrNull() ?: throw NoSuchEntityException()

            val results = (activeSurvies[uniqueID] ?: throw NoSuchEntityException()).getResults()
            activeSurvies.remove(uniqueID)
            return CompletableFuture.completedFuture(results)
        }

    }

}


