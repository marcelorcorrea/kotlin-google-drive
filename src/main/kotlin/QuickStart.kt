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
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

fun main(args: Array<String>) = runBlocking {
    val quickstart = Quickstart()
    quickstart.start(args.first())
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


    suspend fun start(args: String) {
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
                    val childReference = try {
                        driveService.children().get(folderId, it.id).execute()
                    } catch (ex: IOException) {
                        ex.printStackTrace()
                        it
                    }
                    val f = driveService.files().get(childReference.id).execute()
                    val link = file.webContentLink ?: file.alternateLink
                    async {
                        download(f.title, link)
                    }
                }.forEach { it.join() }
            }
        } else {
            val link = file.webContentLink ?: file.alternateLink
            val deferred = async { download(file.title, link) }
            deferred.join()
        }
    }

    private fun download(title: String, link: String): java.io.File {
        println("Downloading $title - $link")
        val response = driveService.requestFactory.buildGetRequest(GenericUrl(link)).execute()
        return response.download(DESTINY_FOLDER.format(title))
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