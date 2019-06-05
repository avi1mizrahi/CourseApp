package il.ac.technion.cs.softwaredesign

import com.authzee.kotlinguice4.KotlinModule
import com.google.inject.Provider
import il.ac.technion.cs.softwaredesign.dataTypeProxies.MessageManager
import il.ac.technion.cs.softwaredesign.messages.MediaType
import il.ac.technion.cs.softwaredesign.messages.Message
import il.ac.technion.cs.softwaredesign.messages.MessageFactory
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import java.util.concurrent.CompletableFuture

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


        // TODO: temporary, remove when completable API is ready
        bind<SyncStorage>().to<AsyncStorageAdapter>()

        class MessageManagerProvider : Provider<MessageFactory> {
            override fun get(): MessageFactory {
                return MessageManager(KeyValueStoreImpl(
                                        AsyncStorageAdapter(
                                                secureStorageProvider.get())
                                    ).scope("messages"))


            }
        }
        bind<MessageFactory>().toProvider(MessageManagerProvider())
    }

}