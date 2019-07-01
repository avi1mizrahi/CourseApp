package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.dataTypeProxies.*
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage


class CourseAppModule : KotlinModule() {

    override fun configure() {

        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()
        bind<KeyValueStore>().to<KeyValueStoreImpl>()
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()

        bind<SyncStorage>().to<AsyncStorageAdapter>()

        bind<MessageFactory>().to<MessageManager>().`in`<Singleton>()
        bind<ChannelManager>().`in`<Singleton>()
        bind<UserManager>().`in`<Singleton>()
        bind<TokenManager>().`in`<Singleton>()
        bind<MessageManager>().`in`<Singleton>()
        bind<Managers>().`in`<Singleton>()

    }

}