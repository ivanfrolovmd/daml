// Copyright (c) 2019 The DAML Authors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.jwt

import java.io.{File, FileInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.cert.CertificateFactory
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.KeyFactory

import com.digitalasset.daml.lf.data.TryOps.Bracket.bracket
import scalaz.Show
import scalaz.syntax.show._

import scala.util.Try

object KeyUtils {
  final case class Error(what: Symbol, message: String)

  object Error {
    implicit val showInstance: Show[Error] =
      Show.shows(e => s"PemUtils.Error: ${e.what}, ${e.message}")
  }

  private val mimeCharSet = StandardCharsets.ISO_8859_1

  /**
    * Reads an RSA public key from a X509 encoded file.
    * These usually have the .crt file extension.
    */
  def readRSAPublicKeyFromCrt(file: File): Try[RSAPublicKey] = {
    bracket(Try(new FileInputStream(file)))(is => Try(is.close())).flatMap { istream =>
      Try(
        CertificateFactory
          .getInstance("X.509")
          .generateCertificate(istream)
          .getPublicKey
          .asInstanceOf[RSAPublicKey])
    }
  }

  /**
    * Reads a RSA private key from a PEM/PKCS#8 file.
    * These usually have the .pem file extension.
    */
  def readRSAPrivateKeyFromPem(file: File): Try[RSAPrivateKey] = {
    bracket(Try(new FileInputStream(file)))(is => Try(is.close())).flatMap { istream =>
      for {
        fileContent <- Try(Files.readAllBytes(file.toPath))

        // Remove PEM container header and footer
        pemContent <- Try(
          new String(fileContent, mimeCharSet)
            .replaceFirst("-----BEGIN ([A-Z ])*-----\n", "")
            .replaceFirst("\n-----END ([A-Z ])*-----\n", "")
            .replace("\r", "")
            .replace("\n", "")
        )

        // Base64-decode the PEM container content
        decoded <- Base64
          .decode(pemContent)
          .leftMap(e => new RuntimeException(e.shows))
          .toEither
          .toTry

        // Interpret the container content as PKCS#8
        key <- Try {
          val kf = KeyFactory.getInstance("RSA")
          val keySpec = new PKCS8EncodedKeySpec(decoded.getBytes)
          kf.generatePrivate(keySpec).asInstanceOf[RSAPrivateKey]
        }
      } yield key
    }
  }

  /**
    * Reads a RSA private key from a binary file (PKCS#8, DER)
    * openssl pkcs8 -topk8 -inform PEM -outform DER -in private-key.pem -nocrypt > private-key.der
    */
  def readRSAPrivateKeyFromDer(file: File): Try[RSAPrivateKey] = {
    bracket(Try(new FileInputStream(file)))(is => Try(is.close())).flatMap { istream =>
      for {
        fileContent <- Try(Files.readAllBytes(file.toPath))

        // Interpret the container content as PKCS#8
        key <- Try {
          val kf = KeyFactory.getInstance("RSA")
          val keySpec = new PKCS8EncodedKeySpec(fileContent)
          kf.generatePrivate(keySpec).asInstanceOf[RSAPrivateKey]
        }
      } yield key
    }
  }

  /**
    * Generates a JWKS JSON object for the given map of KeyID->Key
    *
    * Note: this uses the same format as Google OAuth, see https://www.googleapis.com/oauth2/v3/certs
    */
  def generateJwks(keys: Map[String, RSAPublicKey]): String = {
    def generateKeyEntry(keyId: String, key: RSAPublicKey): String =
      s"""    {
         |      "kid": "$keyId",
         |      "kty": "RSA",
         |      "alg": "RS256",
         |      "use": "sig",
         |      "e": "${java.util.Base64.getUrlEncoder
           .encodeToString(key.getPublicExponent.toByteArray)}",
         |      "n": "${java.util.Base64.getUrlEncoder.encodeToString(key.getModulus.toByteArray)}"
         |    }""".stripMargin

    s"""
       |{
       |  "keys": [
       |${keys.toList.map { case (keyId, key) => generateKeyEntry(keyId, key) }.mkString(",\n")}
       |  ]
       |}
    """.stripMargin
  }
}
