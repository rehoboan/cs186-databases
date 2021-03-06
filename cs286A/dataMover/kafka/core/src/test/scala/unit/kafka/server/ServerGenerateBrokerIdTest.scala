/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  *    http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package kafka.server

import kafka.zk.ZooKeeperTestHarness
import kafka.utils.{TestUtils, Utils}
import org.junit.Test
import org.scalatest.junit.JUnit3Suite
import junit.framework.Assert._
import java.io.File

class ServerGenerateBrokerIdTest extends JUnit3Suite with ZooKeeperTestHarness {
  var props1 = TestUtils.createBrokerConfig(-1, TestUtils.choosePort)
  var config1 = KafkaConfig.fromProps(props1)
  var props2 = TestUtils.createBrokerConfig(0, TestUtils.choosePort)
  var config2 = KafkaConfig.fromProps(props2)
  val brokerMetaPropsFile = "meta.properties"


  @Test
  def testAutoGenerateBrokerId() {
    var server1 = new KafkaServer(config1)
    server1.startup()
    server1.shutdown()
    assertTrue(verifyBrokerMetadata(config1.logDirs, 1001))
    // restart the server check to see if it uses the brokerId generated previously
    server1 = new KafkaServer(config1)
    server1.startup()
    assertEquals(server1.config.brokerId, 1001)
    server1.shutdown()
    Utils.rm(server1.config.logDirs)
    TestUtils.verifyNonDaemonThreadsStatus
  }

  @Test
  def testUserConfigAndGeneratedBrokerId() {
    // start the server with broker.id as part of config
    val server1 = new KafkaServer(config1)
    val server2 = new KafkaServer(config2)
    val props3 = TestUtils.createBrokerConfig(-1, TestUtils.choosePort)
    val config3 = KafkaConfig.fromProps(props3)
    val server3 = new KafkaServer(config3)
    server1.startup()
    assertEquals(server1.config.brokerId,1001)
    server2.startup()
    assertEquals(server2.config.brokerId,0)
    server3.startup()
    assertEquals(server3.config.brokerId,1002)
    server1.shutdown()
    server2.shutdown()
    server3.shutdown()
    assertTrue(verifyBrokerMetadata(server1.config.logDirs,1001))
    assertTrue(verifyBrokerMetadata(server2.config.logDirs,0))
    assertTrue(verifyBrokerMetadata(server3.config.logDirs,1002))
    Utils.rm(server1.config.logDirs)
    Utils.rm(server2.config.logDirs)
    Utils.rm(server3.config.logDirs)
    TestUtils.verifyNonDaemonThreadsStatus
  }

  @Test
  def testMultipleLogDirsMetaProps() {
    // add multiple logDirs and check if the generate brokerId is stored in all of them
    val logDirs = props1.getProperty("log.dir")+ "," + TestUtils.tempDir().getAbsolutePath +
    "," + TestUtils.tempDir().getAbsolutePath
    props1.setProperty("log.dir",logDirs)
    config1 = KafkaConfig.fromProps(props1)
    var server1 = new KafkaServer(config1)
    server1.startup()
    server1.shutdown()
    assertTrue(verifyBrokerMetadata(config1.logDirs, 1001))
    // addition to log.dirs after generation of a broker.id from zk should be copied over
    val newLogDirs = props1.getProperty("log.dir") + "," + TestUtils.tempDir().getAbsolutePath
    props1.setProperty("log.dir",newLogDirs)
    config1 = KafkaConfig.fromProps(props1)
    server1 = new KafkaServer(config1)
    server1.startup()
    server1.shutdown()
    assertTrue(verifyBrokerMetadata(config1.logDirs, 1001))
    Utils.rm(server1.config.logDirs)
    TestUtils.verifyNonDaemonThreadsStatus
  }

  @Test
  def testConsistentBrokerIdFromUserConfigAndMetaProps() {
    // check if configured brokerId and stored brokerId are equal or throw InconsistentBrokerException
    var server1 = new KafkaServer(config1) //auto generate broker Id
    server1.startup()
    server1.shutdown()
    server1 = new KafkaServer(config2) // user specified broker id
    try {
      server1.startup()
    } catch {
      case e: kafka.common.InconsistentBrokerIdException => //success
    }
    server1.shutdown()
    Utils.rm(server1.config.logDirs)
    TestUtils.verifyNonDaemonThreadsStatus
  }

  def verifyBrokerMetadata(logDirs: Seq[String], brokerId: Int): Boolean = {
    for(logDir <- logDirs) {
      val brokerMetadataOpt = (new BrokerMetadataCheckpoint(
        new File(logDir + File.separator + brokerMetaPropsFile))).read()
      brokerMetadataOpt match {
        case Some(brokerMetadata: BrokerMetadata) =>
          if (brokerMetadata.brokerId != brokerId)  return false
        case _ => return false
      }
    }
    true
  }
}
