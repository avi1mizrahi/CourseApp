package il.ac.technion.cs.softwaredesign

import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.dataTypeProxies.Managers
import java.util.concurrent.CompletableFuture

class CourseAppStatisticsImpl @Inject constructor(private val managers: Managers): CourseAppStatistics {
    override fun pendingMessages(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(managers.messages.messageListner.statistics.getTotalPrivatePending())
    }

    override fun channelMessages(): CompletableFuture<Long> {
        return CompletableFuture.completedFuture(managers.messages.statistics.getTotalChannelMessages())
    }

    override fun top10ChannelsByMessages(): CompletableFuture<List<String>> {
        return CompletableFuture.completedFuture(managers.channels.statistics.getTop10ChannelsByMessageCount())

    }

    override fun totalUsers(): CompletableFuture<Long> = completedOf(managers.users.getUserCount().toLong())

    override fun loggedInUsers(): CompletableFuture<Long> = completedOf(managers.users.getActiveCount().toLong())

    override fun top10ChannelsByUsers(): CompletableFuture<List<String>> = completedOf(managers.channels.statistics.getTop10ChannelsByUserCount())

    override fun top10ActiveChannelsByUsers(): CompletableFuture<List<String>> = completedOf(managers.channels.statistics.getTop10ChannelsByActiveUserCount())

    override fun top10UsersByChannels(): CompletableFuture<List<String>> = completedOf(managers.users.statistics.getTop10UsersByChannel())
}