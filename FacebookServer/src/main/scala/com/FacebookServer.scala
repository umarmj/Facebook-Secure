package com

import akka.actor._
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import akka.routing.RoundRobinRouter

import scala.collection.mutable._
import scala.util.Random
import scala.concurrent._
import scala.concurrent.duration._

import spray.can.Http
import spray.routing._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import org.json4s.native.Serialization
import org.json4s.native.Serialization._
import org.json4s.ShortTypeHints
import org.json4s.native.Json
import org.json4s.DefaultFormats

import MediaTypes._

import java.security._
import com.RSA

//profile of a user
case class Profile(userName: String,dob: String, gender:String, phoneNumber:String, emailId:String) extends Serializable
case class Post(author: String,content: String,likes: Int) extends Serializable
case class Picture(PhotoId: String, Description: String, time: String, from: Int, location: String) extends Serializable

case class UserPostHashMap(posts: HashMap[String,Post])
case class UserFriendList(friends: HashMap[String, ListBuffer[String]])
case class UserPictures (pictures: ListBuffer[Picture])
//FacebookUser Actions
case class SetProfile(userNum: Int, dob:String, gender:String, phoneNumber:String, publicKey: String) 
case class PostonWall(postContent: String, id: String)
case class LikePost(postId:String)
case class AddFriend(friendUserName: String)
case class UploadPhoto(photoId: String, description: String, time: String, from: Int, location: String, albumId: String)
case class AddPicturetoAlbum(photoId: String, albumId: String)
case class LoginUser(sessionId: String)
case class GetPostById(postId: String)
case class GetPhotoById(photoId: String)
case object GetProfile
case object GetPosts
case object GetFriends
case object SendFriendlistForAuthentication
case object GetPictures
case object GetPublicKey
case object CheckUser


//case class PrepareStuff(numberOfUsers:Int)
case class GetAlbums(userNum:Int)
//case class AddFriend(to:Int, from:Int)
//case class GetProfile(userNum:Int)
//case class GeneratePageForDisplay(userNum:Int)
//object GetFriends

object Boot extends App with SimpleRoutingApp{

  override def main(args: Array[String]){

    implicit val system = ActorSystem("FacebookServer")
    val actorCount: Int = Runtime.getRuntime().availableProcessors()*100
    implicit val timeout = Timeout(5.seconds)
    println("Server Started!!!")

    lazy val CreateProfile = post{
      path("facebook" / "createUser"){ 
        println("Creating User")
        entity(as[FormData]) { fields =>
        //println("Fields = " + fields)
          val userId = fields.fields(0)._2
          val dob= fields.fields(1)._2
          val gender = fields.fields(2)._2
          val phoneNumber = fields.fields(3)._2
          val puclicKey = fields.fields(4)._2
          val facebookUser = system.actorOf(Props[FacebookUser],name="facebookUser"+userId)
          println(facebookUser) 
          facebookUser!SetProfile(userId.toInt,dob,gender,phoneNumber,puclicKey)
          complete{
            "Done"
          }
        }
      }
    }
    lazy val GetProfileInfo = get{
      respondWithMediaType(MediaTypes.`application/json`)
      path("facebook" / "getUser"/Segment){ userNum => 
        println("Getting user ")
        var userName = "facebookUser"+userNum;
        //println("Fields = " + fields)
        val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
        val future = actor ? GetProfile
        val userProfile = Await.result(future, timeout.duration).asInstanceOf[Profile]
        complete
        {
          ToJson.toJson(userProfile)
        }
      }
    }
    lazy val PostStuff = post{
      path("facebook"/"createPost"){
        println("Posting Stuff on Wall")
        entity(as[FormData]) { fields =>
          val userName = fields.fields(0)._2
          val postContent = fields.fields(1)._2
          val postId = fields.fields(2)._2
          val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
          actor ! PostonWall(postContent, postId)
          complete{
            "Done"
          }
        }
      }
    }
    lazy val GetPostsofUser = get{
      respondWithMediaType(MediaTypes.`application/json`)
      path("facebook" / "getPosts"){ 
        parameters("userName".as[String], "requestFrom".as[String]) { (userName,requestFrom) => 
          println("Getting posts ")
          //var userName = fields.fields(0)._2;
          //var requestFrom = fields.fields(1)._2;
          var userPosts = UserPostHashMap(HashMap[String,Post]())
          if(userName == requestFrom){
            val actor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+userName)
            val future2 = actor ? GetPosts
            userPosts = Await.result(future2, timeout.duration).asInstanceOf[UserPostHashMap]
            println(userPosts)
          }
          else{
            //see if inside friendlist
            //Get friendlist 
            val myActor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+requestFrom)
            val future = myActor ? SendFriendlistForAuthentication
            val userFriends = Await.result(future, timeout.duration).asInstanceOf[ListBuffer[String]]
            if(userFriends.exists(_=="facebookUser"+userName)==true){
              val actor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+userName)
              val future2 = actor ? GetPosts
              userPosts = Await.result(future2, timeout.duration).asInstanceOf[UserPostHashMap]
              println(userPosts)
            }  
          } 
          complete
          {
            ToJson.toJson(userPosts)
          }
        }
      }
    }
    lazy val GetPostsIdofUser = get{
      respondWithMediaType(MediaTypes.`application/json`)
      path("facebook" / "getPostsId"){ 
        parameters("userName".as[String], "requestFrom".as[String], "postId".as[String]) { (userName,requestFrom, postId) => 
          println("Getting posts ")
          //var userName = fields.fields(0)._2;
          //var requestFrom = fields.fields(1)._2;
          var userPost = Post("","",0)
          if(userName == requestFrom){
            val actor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+userName)
            val future2 = actor ? GetPostById(postId)
            userPost = Await.result(future2, timeout.duration).asInstanceOf[Post]
            println(userPost)
          }
          else{
            //see if inside friendlist
            //Get friendlist 
            val myActor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+requestFrom)
            val future = myActor ? SendFriendlistForAuthentication
            val userFriends = Await.result(future, timeout.duration).asInstanceOf[ListBuffer[String]]
            if(userFriends.exists(_=="facebookUser"+userName)==true){
              val actor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+userName)
              val future2 = actor ? GetPostById(postId)
              userPost = Await.result(future2, timeout.duration).asInstanceOf[Post]
              println(userPost)
            }  
          } 
          complete
          {
            ToJson.toJson(userPost)
          }
        }
      }
    }
    lazy val LikePostofSomeone = post{
      path("facebook"/"likePost"){
        println("Like Posts")
        entity(as[FormData]) { fields =>
          val userName = fields.fields(0)._2
          val postId = fields.fields(1)._2
          val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
          actor ! LikePost(postId)
          complete{
            "Done"
          }
        }
      }
    }
    lazy val SendFriendRequest = post{
      path("facebook"/"friendRequest"){
        println("sending friend request")
          entity(as[FormData]) { fields =>
          val from = fields.fields(0)._2
          val to = fields.fields(1)._2
          val userName = "facebookUser" + from
          val friendUserName = "facebookUser" + to
          val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
          val friendActor = system.actorSelection("akka://FacebookServer/user/"+friendUserName)

          actor ! AddFriend(friendUserName)
          friendActor ! AddFriend(userName)
          complete{
            "Done"
          }
        }
      }
    }
    lazy val GetFriendsofUser = get{
       respondWithMediaType(MediaTypes.`application/json`)
      path("facebook" / "getFriends"/Segment){userNum => 
        println("Getting Friends ")
        var userName = "facebookUser"+userNum;
        //println("Fields = " + fields)
        val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
        val future = actor ? GetFriends
        val userFriends = Await.result(future, timeout.duration).asInstanceOf[UserFriendList]
        
        //var mapuser = userPosts.toMap

        println(userFriends)
        complete
        {
          //scala.util.parsing.json.JSONObject(mapuser)
          ToJson.toJson(userFriends)
        }
      }
    }
    lazy val UploadPicture = post{
      path("facebook"/"uploadPicture"){
        println("Uploading Picture")
          entity(as[FormData]) { fields =>
          val from = fields.fields(0)._2
//case class Picture(PhotoId: String, Description: String time: String, from: Int, location: String) extends Serializable
          
          val description = fields.fields(1)._2
          val photoId = "photo"+fields.fields(2)._2
          val albumId = fields.fields(3)._2
          val userName = "facebookUser" + from


          val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
          actor ! UploadPhoto(photoId, description, System.currentTimeMillis.toString, from.toInt, "Gaineville",  albumId)

          complete{
            "Done"
          }
        }
      }
    }
    lazy val GetPicturesofUser = get{
      respondWithMediaType(MediaTypes.`application/json`)
      path("facebook" / "getPictures"/Segment){userNum => 
        println("Getting Pictures ")
        var userName = "facebookUser"+userNum;
        //println("Fields = " + fields)
        val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
        val future = actor ? GetPictures
        val userPictures = Await.result(future, timeout.duration).asInstanceOf[UserPictures]
        
        //var mapuser = userPosts.toMap

        println(userPictures)
        complete
        {
          //scala.util.parsing.json.JSONObject(mapuser)
          ToJson.toJson(userPictures)
        }
      }
    }
    lazy val GetPhotoIdofUser = get{
      respondWithMediaType(MediaTypes.`application/json`)
      path("facebook" / "getPhotoId"){ 
        parameters("userName".as[String],  "photoId".as[String]) { (userName,photoId) => 
          println("Getting picture ")
          //var userName = fields.fields(0)._2;
          //var requestFrom = fields.fields(1)._2;
          var userPicture = Picture("","","",0,"")
          val actor = system.actorSelection("akka://FacebookServer/user/"+"facebookUser"+userName)
          val future2 = actor ? GetPhotoById(photoId)
          userPicture = Await.result(future2, timeout.duration).asInstanceOf[Picture]
          
          complete
          {
            ToJson.toJson(userPicture)
          }
        }
      }
    }
    lazy val Login = post{
      path("facebook"/"login"){
        println("user wants to login")
          entity(as[FormData]) { fields =>
          val id = fields.fields(0)._2
          val userName = "facebookUser" + id
          val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
          val future = actor ? GetPublicKey
          val key = Await.result(future, timeout.duration).asInstanceOf[String]
          val userKey = RSA.decodePublicKey(key)

          var prng = new SecureRandom()
          var bytes = new Array[Byte](128)
          var randomNumberSent = prng.nextBytes(bytes).toString
          //var randomNumberSent = prng.nextInt(128).toString

          actor ! LoginUser(randomNumberSent)

          println("Random nunmber sent for authentication for id " + id + " is: " + randomNumberSent)
          var stringToSendback = RSA.encrypt(userKey,randomNumberSent)

          complete{
            stringToSendback
          }
        }
      }
    }
    lazy val Authenticate = post{ 
      path("facebook"/"authenticate"){
          entity(as[FormData]) { fields =>
          val id = fields.fields(0)._2
          val sessionId = fields.fields(1)._2
          val userName = "facebookUser" + id
          val actor = system.actorSelection("akka://FacebookServer/user/"+userName)
          val future = actor ? CheckUser
          val userSession = Await.result(future, timeout.duration).asInstanceOf[String]
          var SendAck = "Welcome User"
          if (userSession != sessionId){
            SendAck = "Wrong User: Not Authenticated"
          }
          complete{
            SendAck
          }
        }
      }
    }
    startServer(interface = "localhost", port = 8080) {
      CreateProfile~
      GetProfileInfo~
      PostStuff~
      LikePostofSomeone~
      GetPostsofUser~
      GetPostsIdofUser~
      SendFriendRequest~
      GetFriendsofUser~
      UploadPicture~
      GetPicturesofUser~
      GetPhotoIdofUser~
      Login~
      Authenticate
    }
  }
}




class FacebookUser() extends Actor{
  var profile:Profile = Profile("","","","","")
  var userName:String = ""
  var emailId:String = ""
  var friendList  = ListBuffer[String]()
  var friendCount : Int = 0
  var posts = HashMap[String, Post](); 
  var pictures = ListBuffer[Picture]();
  var albums = HashMap[String, ListBuffer[String]]() //list of pictureID

  var sessionIdOfThisUser = ""
  var publicKeyOfThisUser = ""

  def receive = {
    case SetProfile(userNum: Int, dob:String, gender:String, phoneNumber:String, publicKey: String)  => {
      println("Im here")
      userName = "FbUser" + userNum;
      emailId = userName + "@ufl.edu"
      publicKeyOfThisUser = publicKey
      profile = Profile(userName,dob,gender,phoneNumber,emailId)
    }
    case GetProfile => {
      sender ! profile
    }
    case PostonWall(postContent: String, id: String) => {
      println("Im posting")
      val post = Post(userName, postContent, 0)
      posts += (id -> post)
    }
    case GetPosts => {
      sender ! UserPostHashMap(posts)
    }
    case GetPostById(postId: String) => {
      if(posts.contains(postId)){
        var postObject = posts.get(postId) match{
          case Some(postObject) => postObject
          case None => Post("Error","Error",0)
        }
        sender ! postObject
      }
    }
    case LikePost(postId: String) => {
      if(posts.contains(postId)){
        var postObject = posts.get(postId) match{
          case Some(postObject) => postObject
          case None => Post("Error","Error",0)
        }
        var newPostObj = Post(postObject.author,postObject.content,postObject.likes+1)
        posts += (postId -> newPostObj)
      }
    }
    case AddFriend(friend: String) =>{
      if(friendList.exists(_==friend)==false){
        friendList += friend;
      } 
    }
    case GetFriends => {
      var friendHashMap = HashMap[String, ListBuffer[String]]()
      friendHashMap += (userName -> friendList)
      sender ! UserFriendList(friendHashMap) 
    }  
    case SendFriendlistForAuthentication => {
      sender ! friendList
    }
    case UploadPhoto(photoId: String, description: String, time: String, from: Int, location: String, albumId: String) => {
      println("Im uploading")
      pictures += Picture(photoId, description, time, from, location) 
      self ! AddPicturetoAlbum(photoId, albumId)
    }
    case AddPicturetoAlbum(photoId: String, albumId: String) => {
      println("addingPicturetoAlbum")
      if(!albums.contains(albumId)){
        var tempList = ListBuffer[String]()
        tempList += photoId
        albums += (albumId -> tempList)
      }
      else{
        var pictures= albums.get(albumId) match{
          case Some(pictures) => pictures
          case None => ListBuffer[String]()
        }
        pictures += photoId
        albums += (albumId -> pictures)
      }
    }
    case GetPhotoById(photoId: String) => {
      sender ! pictures(photoId.toInt)
    }
    case GetPictures => {
      sender ! UserPictures(pictures)
    }
    case GetPublicKey => {
      sender ! publicKeyOfThisUser
    }
    case LoginUser(sessionId: String) => {
      sessionIdOfThisUser = sessionId
    }
    case CheckUser => {
      /*if (sessionIdOfThisUser == sessionId){
        println("He is in")
      }*/
      sender ! sessionIdOfThisUser
    }
  }
}

object ToJson{
  implicit val formats = Serialization.formats(ShortTypeHints(List(classOf[Profile], classOf[UserPostHashMap], classOf[UserFriendList], classOf[UserPictures], classOf[Post], classOf[Picture])))
  def toJson(profile:Profile) : String = writePretty(profile)
  def toJson(posts:UserPostHashMap) : String = writePretty(posts)
  def toJson(friends: UserFriendList) : String = writePretty(friends)
  def toJson(pictures: UserPictures) : String = writePretty(pictures)
  def toJson(post: Post) : String = writePretty(post)  
  def toJson(picture: Picture) : String = writePretty(picture)  
}