package com

import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.routing.RoundRobinRouter
import akka.util._

import akka.actor._
import akka.io.IO
import akka.pattern.ask

import scala.collection.mutable._
import spray.http._
import spray.client.pipelining._
import scala.util.Random
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.concurrent.duration._

import spray.json._
import scala.util.parsing.json.JSONObject

import spray.httpx.SprayJsonSupport._

import org.apache.commons.codec.binary.Base64

import java.security.MessageDigest

import java.security._
import java.security.spec.KeySpec
import javax.crypto.spec.{PBEKeySpec, SecretKeySpec, IvParameterSpec}
import javax.crypto.{SecretKeyFactory, Cipher, KeyGenerator, SecretKey}
import org.apache.commons.codec.binary.Base64
import java.util._

import org.json4s._
import org.json4s.native.JsonMethods._

import com.{RSA,AES}

case class Start(system:  ActorSystem)
case class StartUsers(system: ActorSystem)
case class Send(userCount: Int)

//user tasks
case class CreateUser(id: String, dob: String, gender: String, phoneNumber: String)
case class PostOnMyWall(id: String, postContent: String, postId: String)
case class SendFriendRequest(from: String, to: String)
case class GetMyPosts(userName: String, requestFrom: String)
case class GetMyPostsById(userName: String, requestFrom: String, postId: String)
case class UploadPicture(userId: String, description: String, photoId: String, albumId: String)
case class Login(id:String)
case class Authenticate(id:String, keyToBeDecrypted: String)
case class GetMyPictureById(userid: String, pictureId: String)
case class GetFriendInformation(publicKeyOfFriend: PublicKey)
case class GetPostInformation(postId: String)
case class GetFriends(id: String) 

case class IV(iv: Array[Byte])

object Simulator extends App{
  private val startTime: Long = System.currentTimeMillis
  override def main(args: Array[String]){
    implicit val system = ActorSystem("Simulator")
      println("Enter the number of users to simulate:")
      val numberOfUsers = readInt
      val simulator = system.actorOf(Props(new MasterClient(system, numberOfUsers)), name = "FacebookSimulator")
      simulator ! Start(system)
  } 
}


// Master Actor that handles the routes.
class MasterClient(system: ActorSystem, numberOfUsers: Int) extends Actor{
  import system.dispatcher
  val pipeline = sendReceive 
  var userTracker = new ArrayBuffer[ActorRef]()
  def receive={
    case Start(system) =>{
      //create users - actor for each user. 
      for (i <- 0 until numberOfUsers){
        userTracker += context.actorOf(Props(new User(system)),name="FUser"+i)
        //println(userTracker(i))
        val dd = 1 + Random.nextInt(28) 
        val mm = 1 + Random.nextInt(11)
        val yyyy = 1980 + Random.nextInt(25) 
        val dob : String = mm.toString+"/"+dd.toString+"/"+yyyy.toString

        var gender = "M"
        if(i%3==0){
          gender = "F"
        }
        val areaCode = 300 + Random.nextInt(600)
        val firstPart = 1+ Random.nextInt(500)
        val secondPart = 1000 + Random.nextInt(2000)
        val phoneNumber : String = "("+areaCode.toString+")"+""+firstPart.toString+"-"+secondPart.toString

        userTracker(i) ! CreateUser(i.toString, dob, gender, phoneNumber)
        userTracker(i) ! Login(i.toString)
      }
      Thread.sleep(5000)
      for ( i <- 0 until numberOfUsers){
        userTracker(i) ! PostOnMyWall("facebookUser"+i.toString, "First post of user "+i.toString, "1")
        userTracker(i) ! PostOnMyWall("facebookUser"+i.toString, "Second post of user "+i.toString, "2")
        userTracker(i) ! PostOnMyWall("facebookUser"+i.toString, "Third post of user "+i.toString, "3")
      }
      
      for ( i <- 0 until numberOfUsers){
        userTracker(i) ! UploadPicture(i.toString, "First Picture of User "+i.toString, "0", "1")
      }
      //allow time to set up the users
      userTracker(0) ! SendFriendRequest("0","1")

      for (i <- 0 until numberOfUsers){
        for (j <- 0 until 3){
          var randomFriend = Random.nextInt(numberOfUsers)
          while(randomFriend == i){
            randomFriend = Random.nextInt(numberOfUsers)
          }
          userTracker(i) ! SendFriendRequest(i.toString,randomFriend.toString)
        }
      }

      userTracker(0) ! GetMyPostsById("1", "0", "1")

      userTracker(0) ! GetMyPictureById("0", "0")
      userTracker(0) ! GetMyPostsById("0", "0", "1")
      userTracker(1) ! GetMyPostsById("1", "1", "2")
    }

    var randomPostCount = Random.nextInt(2*numberOfUsers)

      println("Total Random number of Posts to be generated: " + randomPostCount)

      for(i <-0 to randomPostCount-1)
      {
        var randomUser = Random.nextInt(numberOfUsers-1)
        println("Post of user " + randomUser + ": ")
        var temp = randomUser.toString
        var randomPost = 1 + Random.nextInt(2)
        var temp1 = randomPost.toString
        userTracker(randomUser) ! GetMyPostsById(temp, temp, temp1)
      }

      var randomPictureCount = Random.nextInt(numberOfUsers)

      println("Total Random number of Pictures to be generated: " + randomPictureCount)

      for(i <-0 to randomPictureCount-1)
      {
        var randomUser = Random.nextInt(numberOfUsers-1)
        println("Picture of user " + randomUser + ": ")
        var temp = randomUser.toString
        var randomPicture = 0
        var temp1 = randomPicture.toString
        userTracker(randomUser) ! GetMyPictureById(temp, temp1)
      }

      userTracker(0) ! GetFriends("0")

    /*case StartUsers(system) => {
      val actorCount: Int = Runtime.getRuntime().availableProcessors()
      println("Actor Count: " + actorCount)
      for(i <-0 until actorCount) 
        {
          userTracker += context.actorOf(Props(new User(system)),name="FUser"+i)
          //println("facebook User "+ userTracker(i)+" started")
          userTracker(i) ! Send(numberOfUsers)
        }
    } */
  }
}

class User(system: ActorSystem) extends Actor{
  import system.dispatcher
  val start:Long=System.currentTimeMillis
  val pipeline1 = sendReceive
  val keyPair = RSA.genKeys
  val privateKey = keyPair.getPrivate()
  var publicKey = keyPair.getPublic()

  var keyForAES = AES.genRandKey

  var ivMap = ListBuffer[Array[Byte]]() //index represents the transactionId which is the postId - 1 

  var ivMapForPictures = ListBuffer[Array[Byte]]()
  def receive = {
    case CreateUser(id: String, dob: String, gender: String, phoneNumber: String) => {
      //generate a key


      /*val aes = AES.genRandKey

      keyForAES = aes*/
      var publicKeyForServer = RSA.encodePublicKey(publicKey)

      println("AES key for the user "+ id + ": " + keyForAES)
      println("public key for the user "+ id + ": " + keyForAES)
      println("private key for the user "+ id + ": " + keyForAES)

      val result = pipeline1(Post("http://localhost:8080/facebook/createUser",FormData(Seq("field1"->id, "field2"->dob, "field3"->gender, "field4"->phoneNumber, "field5"->publicKeyForServer))))
      result.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
      }
    }
    case Login(id:String) => {
      val result = pipeline1(Post("http://localhost:8080/facebook/login", FormData(Seq("field1"->id))))
      result.foreach { response =>
        println(s"Please Authenticate ${response.status}")
        val keyToBeDecrypted = response.entity.asString
        self ! Authenticate(id, keyToBeDecrypted)
      }
    }
    case PostOnMyWall(id: String, postContent: String, postId: String) => {
      val iv = AES.genRandIv
      ivMap += iv

      println("Iv for post " + postId + " of " + id + ": "+iv)
      val encData = AES.encrypt(keyForAES, iv,postContent)
      //println("Iv for " + postId + ": " + iv)
      val result = pipeline1(Post("http://localhost:8080/facebook/createPost",FormData(Seq("field1"->id, "field2"->encData,"field3"->postId))))
      result.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
      }
    }
    case GetMyPosts(userName: String, requestFrom: String) => {
      var fresult = pipeline1(Get("http://localhost:8080/facebook/getPosts?userName="+userName + "&requestFrom="+requestFrom))
      //val fr = Await.result(fresult, 5 seconds)
      //println(fr.parseJson.asJsObject.fields("posts"))
      fresult.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
        //var data = response.entity.toString//.asString//.asString.parseJson
        //var obj = new JSONObject(data) 
        //println(obj)
      }
    }
    case GetMyPostsById(userName: String, requestFrom: String, postId: String) => {
      var fresult = pipeline1(Get("http://localhost:8080/facebook/getPostsId?userName="+userName + "&requestFrom="+requestFrom+"&postId=" + postId))
      //val fr = Await.result(fresult, 5 seconds)
      //println(fr.parseJson.asJsObject.fields("posts"))
      fresult.foreach { response =>
        println(s"Request completed with status ${response.status} and encrypted content:\n${response.entity.asString}")
        implicit val formats = DefaultFormats
        val json = parse(response.entity.asString)
        var content = (json \ "content").extract[String]
        
        if(userName == requestFrom){
          var IvforDec = ivMap(postId.toInt - 1)
          println("decryption iv vector is: " + IvforDec)
          if(!(content.equals("Error")) && !(content.equals(""))){
            var encryptedData = content
            val decryptedData = AES.decrypt(keyForAES,IvforDec,encryptedData)              
            println("\nThe post is: \n" + decryptedData)
          }
        }
        else{
          //need to get the AES key from the friend 
          if(!(content.equals("Error")) && !(content.equals(""))){
            //means that they are fiends. 
            println("Im going to do some crazy stuff now")
            var encryptedData = content
            implicit val timeout = Timeout(5.seconds)
            val actor = system.actorSelection("akka://Simulator/user/FacebookSimulator/FUser"+userName)
            val future = actor ? GetFriendInformation(publicKey)
            val key = Await.result(future, timeout.duration).asInstanceOf[String]

            val decryptedKey = RSA.decrypt(key, privateKey)
            println (decryptedKey)

            val future1 = actor ? GetPostInformation(postId)
            val ivforDec = Await.result(future1, timeout.duration).asInstanceOf[IV]
            println (ivforDec)
            //val decodedIv =java.util.Base64.Decoder.decode(ivforDec.getBytes("UTF-8"))

            val decryptedData = AES.decryptUsingKeyString(decryptedKey,ivforDec.iv,encryptedData)

            println("\nThe post from the other user is: \n" + decryptedData)

          }
        }
        //var data = response.entity.toString//.asString//.asString.parseJson
        //var obj = new JSONObject(data) 
        //println(obj)
      }
    }
    case SendFriendRequest(from: String, to: String) => {
      val result = pipeline1(Post("http://localhost:8080/facebook/friendRequest",FormData(Seq("field1"->from, "field2"->to))))
      result.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
      }
    }
    case Authenticate(id: String, keyToBeDecrypted:String) => {
      println("I am Authenticating with the dec: " + keyToBeDecrypted)
      var toSend = RSA.decrypt(keyToBeDecrypted, privateKey)
      println("the key found is: "+toSend)
      var result = pipeline1(Post("http://localhost:8080/facebook/authenticate",FormData(Seq("field1"->id, "field2"->toSend))))
      result.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
      }
    }
    case UploadPicture(userId: String, description: String, photoId: String, albumId: String) => {
      val iv = AES.genRandIv
      ivMapForPictures += iv
      println("Iv for picture " + photoId + " of " + userId + ": "+iv)
      val encData = AES.encrypt(keyForAES, iv, description)
      val result = pipeline1(Post("http://localhost:8080/facebook/uploadPicture",FormData(Seq("field1"->userId, "field2"->encData,"field3"->photoId, "field4"->albumId ))))
      result.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
      }
    }
    case GetMyPictureById(userid: String, pictureId: String) => {
      var fresult = pipeline1(Get("http://localhost:8080/facebook/getPhotoId?userName="+userid +"&photoId=" + pictureId))
      //val fr = Await.result(fresult, 5 seconds)
      //println(fr.parseJson.asJsObject.fields("posts"))
      fresult.foreach { response =>
        println(s"Request completed with status ${response.status} and encrypted content:\n${response.entity.asString}")
        implicit val formats = DefaultFormats
        val json = parse(response.entity.asString)
        var content = (json \ "Description").extract[String]
        var IvforDec = ivMapForPictures(pictureId.toInt)
        println("decryption iv vector is: " + IvforDec)
        if(!(content.equals("Error")) && !(content.equals(""))){
          var encryptedData = content
          val decryptedData = AES.decrypt(keyForAES,IvforDec,encryptedData)              
          println("\nThe photo description is: \n" + decryptedData)
        }
        //var data = response.entity.toString//.asString//.asString.parseJson
        //var obj = new JSONObject(data) 
        //println(obj)
      }
    }
    case GetFriendInformation(publicKeyOfFriend: PublicKey) => {
      var keyToSend = Base64.encodeBase64String(keyForAES.getEncoded)
      var encryptedKey = RSA.encrypt(publicKeyOfFriend, keyToSend)
      sender ! encryptedKey
    }
    case GetPostInformation(postId: String) => {
      sender ! IV(ivMap(postId.toInt - 1))
      //sender ! Base64.encodeBase64String(ivMap(postId.toInt - 1))
    }
    case GetFriends(id: String) => {
      var fresult = pipeline1(Get("http://localhost:8080/facebook/getFriends/"+id))
      //val fr = Await.result(fresult, 5 seconds)
      //println(fr.parseJson.asJsObject.fields("posts"))
      fresult.foreach { response =>
        println(s"Request completed with status ${response.status} and content:\n${response.entity.asString}")
      }
    }
  }
}

/*class User(system: ActorSystem) extends Actor{
  import system.dispatcher
  val start:Long=System.currentTimeMillis
  val pipeline1 = sendReceive
  def receive ={
    case Send(userCount) =>{
      var userNum = Random.nextInt(userCount-1)
      println(userNum + " is online now")

      var postLength = 10 + Random.nextInt(150)
      var postString = Random.alphanumeric.take(postLength).mkString+""
      //println("Post: " + postString)
      //every online user posts on his wall
      //http://localhost:8080/facebook/addPosts?userNum=10&content=hello&from=2&location=Gainesville
      var result = pipeline1(Post("http://localhost:8080/facebook/addPosts?userNum="+userNum+"&content="+postString+"&from="+userNum+"&location=Gainesville"))
      result.foreach{response =>
        println(s"Posted on my wall: ${response.status} and content:\n${response.entity.asString}")
      }
      //to simulate random behavior
      //if the usernum is 0-10, he requests his page 
      if(userNum/10 == 0){
        var myWall = pipeline1(Get("http://localhost:8080/facebook/page?userNum="+userNum))
         myWall.foreach{response =>
          println(s"my wall: ${response.status} and content:\n${response.entity.asString}")
        }
      }
      //if the user num is 10,20 ...., he requests his friendlist 
      if(userNum%10 == 0){
        var myFriends = pipeline1(Get("http://localhost:8080/facebook/friendlist?userNum="+userNum))
        myFriends.foreach{response =>
          println(s"my friends: ${response.status} and content:\n${response.entity.asString}")
        }
      }
      //if userNum is between 10 and 20, he asks for his photos
      if(userNum > 10 && userNum < 20){
        var myPictures = pipeline1(Get("http://localhost:8080/facebook/photos?userNum="+userNum))
        myPictures.foreach{response =>
          println(s"my photos: ${response.status} and content:\n${response.entity.asString}")
        }
      }
      //if userNum is between 20 and 30, he asks for his first album
      if(userNum > 20 && userNum < 30){
        var myPictures = pipeline1(Get("http://localhost:8080/facebook/albumNumber?userNum="+userNum+"&albumId=0"))
        myPictures.foreach{response =>
          println(s"my photos: ${response.status} and content:\n${response.entity.asString}")
        }
      }
      //if the userNum is between 30 and 40, it sends a friend request to someone
      if(userNum > 30 && userNum < 40){
        var randomFriend = Random.nextInt(userCount-1)
        while(randomFriend==userNum)
        {
          randomFriend = Random.nextInt(userCount-1 )
        }
        var addFriend = pipeline1(Post("http://localhost:8080/facebook/friendrequest?to="+ randomFriend +"&from="+userNum))
        addFriend.foreach{response =>
          println(s"friendrequest completed: ${response.status} and content:\n${response.entity.asString}")
        }
      }
      //if the userNum is between 40 and 50, it uploads a photo to their first album
      if(userNum > 40 && userNum < 50){
        var uploadPhoto = pipeline1(Post("http://localhost:8080/facebook/uploadphoto?userNum="+userNum+"&albumId="+0))
        uploadPhoto.foreach{response =>
          println(s"upload photo completed: ${response.status} and content:\n${response.entity.asString}")
        }
      }
      if(System.currentTimeMillis - start < 60000){   
        self ! Send(userCount)
      }
      else{
        context.system.shutdown()  
      }    
    }
  }
}
*/