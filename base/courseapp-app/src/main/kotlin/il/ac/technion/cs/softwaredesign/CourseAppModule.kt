package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provider
import il.ac.technion.cs.softwaredesign.dataTypeProxies.MessageManager
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage


class CourseAppModule : KotlinModule() {

    override fun configure() {

        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()
        bind<SecureStorage>().toProvider(Provider { CourseAppImplInitializer.storage })
        bind<KeyValueStore>().to<KeyValueStoreImpl>()
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()

        bind<SyncStorage>().to<AsyncStorageAdapter>()

        bind<MessageFactory>().toProvider(Provider {
            MessageManager(KeyValueStoreImpl(AsyncStorageAdapter(CourseAppImplInitializer.storage)).scope(
                    "messages"))
        })
    }

}