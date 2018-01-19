import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.GenericUrl
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

fun main(args: Array<String>) = runBlocking {
    val quickstart = Quickstart()
    quickstart.start()
}

class Quickstart {
    companion object {
        /** Application name.  */
        private const val APPLICATION_NAME = "Google Drive Downloader"

        /** Directory to store user credentials for this application.  */
        private val DATA_STORE_DIR = java.io.File(System.getProperty("user.home"), ".credentials/google-drive-downloader")

        /** Global instance of the [FileDataStoreFactory].  */
        private val DATA_STORE_FACTORY: FileDataStoreFactory = FileDataStoreFactory(DATA_STORE_DIR)

        /** Global instance of the JSON factory.  */
        private val JSON_FACTORY = JacksonFactory.getDefaultInstance()

        /** Global instance of the HTTP transport.  */
        private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()

        private val DESTINY_FOLDER = "${System.getProperty("user.home")}/tmp/%s"

        /** Global instance of the scopes required by this quickstart.
         *
         * If modifying these scopes, delete your previously saved credentials
         * at ~/.credentials/google-drive-downloader
         */
        private val SCOPES = Arrays.asList<String>(DriveScopes.DRIVE_METADATA_READONLY)
    }


    suspend fun start() {
        //        val folderId = "0B0n7M68eOEX6M3Q0VTRuUDFKejg"
//        val folderId = "0B-ZWbNluP6bITFQ4RnYwcVk2U2M"//args[0]
//        val folderId = "0B8Ap9EHcqvn_MjR5cFB6NFRYTlk"//args[0]
//        https://drive.google.com/open?id=0B42vE_NdhWtRV3kzLXZZWU9CYnM
//        val args = "0B42vE_NdhWtRV3kzLXZZWU9CYnM"//args[0]
        val args = "https://drive.google.com/open?id=0B0lue65R4P4WVWxLdmdBVTlHb0k"
        val folderId = extractIdFromUrl(args)
        println("FolderId is $folderId")
        val file = driveService.files().get(folderId).execute()
        println("Title: ${file.title}")
        println("Description: ${file.description}")
        println("MIME type:  ${file.mimeType}")
        println("Download Url: ${file.webContentLink ?: file.alternateLink}")

        if (file.mimeType == "application/vnd.google-apps.folder") {
            val childList = driveService.children().list(folderId).setQ("mimeType = 'application/pdf'").execute()
            val files = childList.items
            if (files == null || files.isEmpty()) {
                println("No files found.")
            } else {

                files.map {
                    val link = file.webContentLink ?: file.alternateLink
                    if (link != null) {
                        async {
                            val f = driveService.files().get(link).execute()
                            download(f)
                        }
                    } else {
                        async {
                            val childReference = driveService.children().get(folderId, it.id).execute()
                            val f = driveService.files().get(childReference.id).execute()
                            download(f)
                        }
                    }
                }.forEach { it.join() }
            }
        } else {
            val deferred = async { download(file) }
            deferred.join()
        }
    }

    private fun download(f: File): java.io.File {
        val link = f.webContentLink ?: f.alternateLink
        println("Downloading ${f.title} - $link")
        val response = driveService.requestFactory.buildGetRequest(GenericUrl(link)).execute()
        return response.download(DESTINY_FOLDER.format(f.title))
    }


    /**
     * Build and return an authorized Drive client service.
     * @return an authorized Drive client service
     * @throws IOException
     */
    private val driveService: Drive by lazy {
        val credential = authorize()
        Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build()
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    @Throws(IOException::class)
    private fun authorize(): Credential {
        // Load client secrets.
        val `in` = Quickstart::class.java.getResourceAsStream("/client_secret.json")
        val clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, InputStreamReader(`in`))

        // Build flow and trigger user authorization request.
        val flow = GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(DATA_STORE_FACTORY)
                .setAccessType("offline")
                .build()
        val credential = AuthorizationCodeInstalledApp(flow, LocalServerReceiver()).authorize("user")
        println("Credentials saved to " + DATA_STORE_DIR.absolutePath)
        return credential
    }

    private fun extractIdFromUrl(url: String): String? {
        val regex = Regex("\b(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")

        if (url.startsWith("https://drive.google.com")) {
            val matcher = Regex("(?<==).*\$")
            val matchResult = matcher.find(url)
            return matchResult?.groups?.first()?.value
        } else if (regex.find(url) != null) {
            throw IllegalArgumentException("You must provide a valid URL")
        }
        val regexUrl = Regex("[a-zA-Z0-9\\-]")
        val matchResult = regexUrl.find(url)
        return if (matchResult != null) url else throw IllegalArgumentException("You must provide a valid id")
    }
}