package com.jworkdev.trading.robot.ib

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.{HttpURLConnection, URL}
import scala.util.{Try, Using}

object IBKROAuthToken:

  private val BASE_URL = "https://localhost:5000/v1/api"

  def main(args: Array[String]): Unit =
    val tokenStatus = getAuthStatus()
    tokenStatus match
      case Some(response) => println(s"Authentication Status: $response")
      case None => println("Failed to retrieve authentication status")

  def getAuthStatus(): Option[String] =
    Try {
      val url = new URL(s"$BASE_URL/iserver/auth/status")
      val connection = url.openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("POST")
      connection.setRequestProperty("Content-Type", "application/json")
      connection
    }.toOption.flatMap { connection =>
      Using(connection.getInputStream) { inputStream =>
        val reader = BufferedReader(InputStreamReader(inputStream))
        reader.lines().toArray.mkString("\n")
      }.toOption
    }

