package com

import java.io._
import java.nio.charset.Charset
import java.security.{KeyFactory, _}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import javax.crypto._
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Base64

object RSA{
  def genKeys = {
    var keyGenRSA = KeyPairGenerator.getInstance("RSA")
	keyGenRSA.initialize(1024)
    var key = keyGenRSA.generateKeyPair()   
    var pubKey = key.getPublic()
    var priKey = key.getPrivate()
	(pubKey, priKey)
  }
  
  def encrypt(pubKey: PublicKey, plainText: String): String = {
    var cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, pubKey)
    var encryptedByteArray = cipher.doFinal(plainText.getBytes())
    var encryptedText = Base64.encodeBase64String(encryptedByteArray)
    return encryptedText
  }
  def decrypt(encryptedText: String,  priKey:PrivateKey): String = {
      var cipher = Cipher.getInstance("RSA");
      cipher.init(Cipher.DECRYPT_MODE, priKey);
      var decryptedText = cipher.doFinal(Base64.decodeBase64(encryptedText));
      return new String(decryptedText);
  }
  def encodePublicKey(key: PublicKey): String = {
    Base64.encodeBase64String(key.getEncoded())
  }
  def decodePublicKey(encodedKey: String): PublicKey = {
    val publicBytes = Base64.decodeBase64(encodedKey)
    val keySpec = new X509EncodedKeySpec(publicBytes)
    val keyFactory = KeyFactory.getInstance("RSA")
    keyFactory.generatePublic(keySpec)
  }   
}