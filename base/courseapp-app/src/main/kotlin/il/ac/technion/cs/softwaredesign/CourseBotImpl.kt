package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.calculator.calculate
import il.ac.technion.cs.softwaredesign.exceptions.NoSuchEntityException
import il.ac.technion.cs.softwaredesign.exceptions.UserNotAuthorizedException
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture
import kotlin.random.Random


private fun getSenderFromChannelMessageSource(source : String) : String {
    return source.split("@")[1]
}
private fun getChannelFromChannelMessageSource(source : String) : String {
    return source.split("@")[0]
}


class CourseBotManager(val app : CourseApp, val messageFactory : MessageFactory) : CourseBots {
    override fun bot(name: String?): CompletableFuture<CourseBot> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun bots(channel: String?): CompletableFuture<List<String>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    inner class CourseBotInstance() : CourseBot{
        lateinit var token : String




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

        init {
            app.addListener(token, onMessage)
        }
        fun destroy(){
            app.removeListener(token, onMessage)
        }


        private fun resetStatisticsForChannel(channelName: String) {

        }
        private fun addChannel(channelName: String) {

        }
        private fun removeChannel(channelName: String) {
            resetStatisticsForChannel(channelName)
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
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }


        ///////////////// Message counter
        private fun handleMessageForCounting(source: String, message: Message) {
            val channel = getChannelFromChannelMessageSource(source)
            val messagestr = message.contents.toString()
            messageCounters.forEach {
                val regex = it.key.first
                val mediatype = it.key.second

                if (mediatype != null && mediatype != message.media)
                    return

                if (regex != null && !kotlin.text.Regex.fromLiteral(regex).matches(messagestr.toString()))
                    return

                it.value[channel] = (it.value[channel] ?: 0) + 1
            }
        }


        // (Regex?, MediaType?) -> (Channel) -> (Count)
        val messageCounters = HashMap<Pair<String?, MediaType?>, HashMap<String, Int>>()
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
        private fun handleMessageForCalc(source: String, message: Message) {
            val messagestr = message.contents.toString()
            if (!messagestr.startsWith(calcTrigger ?: return)) {
                return
            }
            val res = calculate(messagestr.substring(calcTrigger!!.length + 1))

            messageFactory.create(MediaType.TEXT, res.toString().toByteArray()).thenApply {
                app.channelSend(token, getChannelFromChannelMessageSource(source), it)
            }.join()

        }

        var calcTrigger : String? = null
        override fun setCalculationTrigger(trigger: String?): CompletableFuture<String?> {
            val old = this.calcTrigger
            this.calcTrigger = trigger
            return CompletableFuture.completedFuture(old)
        }


        /////////////////// Tips TODO
        private fun handleMessageForTip(source: String, message: Message) {

        }

        var tipTrigger : String? = null
        override fun setTipTrigger(trigger: String?): CompletableFuture<String?> {
            val old = this.tipTrigger
            this.tipTrigger = trigger
            return CompletableFuture.completedFuture(old)
        }

        override fun richestUser(channel: String): CompletableFuture<String?> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }


        /////////////////// Last Seen
        var seenTime = HashMap<String, LocalDateTime>()
        private fun handleMessageLastSeen(source: String, message: Message) {
            val sender = getSenderFromChannelMessageSource(source)
            seenTime[sender] = message.created
        }
        override fun seenTime(user: String): CompletableFuture<LocalDateTime?> {
            val seen = seenTime[user] // Null if user not found
            return CompletableFuture.completedFuture(seen)
        }



        // Most messages
        private fun handleMessageForMostActive(source: String, message: Message) {
            val user = getSenderFromChannelMessageSource(source)


            //TODO
        }
        override fun mostActiveUser(channel: String): CompletableFuture<String?> {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }




        /////////////////// Survey
        private fun handleMessageForSurvies(source: String, message: Message) {
            activeSurvies.forEach {
                it.value.onMessage(source, message)
            }
        }

        private inner class Survey(var channel : String, var question: String, val answers: List<String>, val randomtoken: String) {
            // Answer -> Vote count
            val results = HashMap<String, Int>()

            // User name -> Answer
            val votes = HashMap<String, String>()
            init {
                answers.forEach {
                    results[it] = 0
                }
            }

            fun onMessage(source: String, message: Message) {
                val answer = results.keys.find { message.contents.toString().contains(it)  } ?: return
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
        private val activeSurvies = HashMap<String, Survey>()
        override fun runSurvey(channel: String, question: String, answers: List<String>): CompletableFuture<String> {
            // TODO throw if bot is not in channel

            val randomidentifier = Random.nextLong(0, Long.MAX_VALUE).toString()
            activeSurvies[randomidentifier] = (Survey(channel, question, answers, randomidentifier))

            return messageFactory.create(MediaType.TEXT, question.toByteArray()).thenApply {
                app.channelSend(token, channel, it)
            }.thenApply {
                randomidentifier
            }
        }

        override fun surveyResults(identifier: String): CompletableFuture<List<Long>> {
            val results = (activeSurvies[identifier] ?: throw NoSuchEntityException()).getResults()
            activeSurvies.remove(identifier)
            return CompletableFuture.completedFuture(results)
        }

    }

}


