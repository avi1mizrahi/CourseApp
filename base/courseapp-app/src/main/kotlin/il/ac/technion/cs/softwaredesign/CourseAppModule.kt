package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provider
import il.ac.technion.cs.softwaredesign.dataTypeProxies.MessageManager
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage

class SecureStorageProvider : Provider<SecureStorage> {
    override fun get(): SecureStorage {
        return CourseAppImplInitializer.storage
    }
}


class CourseAppModule : KotlinModule() {

    override fun configure() {
        val secureStorageProvider = SecureStorageProvider()

        bind<CourseAppInitializer>().to<CourseAppImplInitializer>()
        bind<SecureStorage>().toProvider(secureStorageProvider)
        bind<KeyValueStore>().to<KeyValueStoreImpl>()
        bind<CourseApp>().to<CourseAppImpl>()
        bind<CourseAppStatistics>().to<CourseAppStatisticsImpl>()


        bind<SyncStorage>().to<AsyncStorageAdapter>()

        bind<MessageFactory>().toProvider(Provider<MessageFactory> {
            MessageManager(KeyValueStoreImpl(AsyncStorageAdapter(secureStorageProvider.get())).scope(
                    "messages"))
        })
    }

}