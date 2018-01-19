import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import kotlin.concurrent.thread

fun main(args: Array<String>) = runBlocking<Unit> {
    //    val job = launch { // launch new coroutine and keep a reference to its Job
//        delay(1000L)
//        println("World!")
//    }
//    println("Hello,")
//    job.join() // wait until child coroutine completes

    val matcher = Regex("(?<==).*\$")



    val matcher2 = Regex("\b(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
    val url = "https://drive.google.com/open?id=0B42vE_NdhWtRV3kzLXZZWU9CYnM"
    val find = matcher.find(url)
    println("matcher: $find")


    val regex = Regex("googlsse|dssrive")
    println(regex.find(url)?.groups?.size)
    println(regex.matches(url))



    if (url.startsWith("https://drive.google.com")) {
        val regex = Regex("(?<==).*\$")
        val matches = regex.find(url)
        println(matches?.groupValues)
//            return matches[0]

    }
}