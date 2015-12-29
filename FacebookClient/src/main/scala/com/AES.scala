package com

import java.security._
import java.security.spec.KeySpec
import javax.crypto.spec.{PBEKeySpec, SecretKeySpec, IvParameterSpec}
import javax.crypto.{SecretKeyFactory, Cipher, KeyGenerator, SecretKey}
import org.apache.commons.codec.binary.Base64
import java.util

//import sun.misc.BASE64Encoder

object AES{
  val AES_KEYLENGTH = 128
  def genRandKey(): SecretKey = {
    var keyGenAES = KeyGenerator.getInstance("AES")
    keyGenAES.init(128)
    return keyGenAES.generateKey()
  }
  
  def genRandIv(): Array[Byte] = {
    var aesIv = new Array[Byte](AES_KEYLENGTH/8)
    var prng = new SecureRandom()
    prng.nextBytes(aesIv)
    return aesIv
  }
  
  def encrypt(key: SecretKey, iv: Array[Byte], plainText: String): String = {   
    var cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv))
    var encryptedByteArray = cipher.doFinal(plainText.getBytes())
    var encryptedText =  Base64.encodeBase64String(encryptedByteArray)
    return encryptedText
  }
  
  def decrypt(key: SecretKey, iv: Array[Byte], encryptedText: String): String = {
    var cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv))
    var plainText = cipher.doFinal(Base64.decodeBase64(encryptedText))
    return new String(plainText)
  }

  def decryptUsingKeyString(key: String, iv: Array[Byte], encryptedText: String): String = {
    val keyByte = Base64.decodeBase64(key)
    val secretKey = new SecretKeySpec(keyByte, "AES")
    var cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv))
    var plainText = cipher.doFinal(Base64.decodeBase64(encryptedText))
    return new String(plainText)
  }
}