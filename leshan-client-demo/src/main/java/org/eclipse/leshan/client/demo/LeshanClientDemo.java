// Copyright (c) 2013-2015 Sierra Wireless and others.
//
// All rights reserved. This program and the accompanying materials
// are made available under the terms of the Eclipse Public License v1.0
// and Eclipse Distribution License v1.0 which accompany this distribution.
//
// The Eclipse Public License is available at
//    http://www.eclipse.org/legal/epl-v10.html
// and the Eclipse Distribution License is available at
//    http://www.eclipse.org/org/documents/edl-v10.html.
//
// Contributors:
//     Zebra Technologies - initial API and implementation
//     Sierra Wireless, - initial API and implementation
//     Bosch Software Innovations GmbH, - initial API and implementation


package org.eclipse.leshan.client.demo;

import static org.eclipse.leshan.LwM2mId.*;
import static org.eclipse.leshan.client.object.Security.*;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.californium.core.network.config.NetworkConfig;
import org.eclipse.leshan.LwM2m;
import org.eclipse.leshan.client.californium.LeshanClient;
import org.eclipse.leshan.client.californium.LeshanClientBuilder;
import org.eclipse.leshan.client.object.Server;
import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.client.resource.ObjectsInitializer;
import org.eclipse.leshan.core.model.LwM2mModel;
import org.eclipse.leshan.core.model.ObjectLoader;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.request.BindingMode;
import org.eclipse.leshan.util.Hex;
import org.eclipse.leshan.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LeshanClientDemo {

  private static final Logger LOG = LoggerFactory.getLogger(LeshanClientDemo.class);

  private final static String[] modelPaths = new String[]{
      "3303.xml", "3304.xml", "10266.xml"
  };

  private static final int OBJECT_ID_TEMPERATURE_SENSOR = 3303;
  private static final int OBJECT_ID_WATER_FLOW_SENSOR = 10266;
  private static final int OBJECT_ID_HUMIDITY_SENSOR = 3304;

  private final static String DEFAULT_ENDPOINT = "LeshanClientDemo";
  private final static String USAGE = "java -jar leshan-client-demo.jar [OPTION]\n\n";

  public static void main(final String[] args) {

    // Define options for command line tools
    Options options = defineCommandLineOptions();

    HelpFormatter formatter = new HelpFormatter();
    formatter.setWidth(90);
    formatter.setOptionComparator(null);

    // Parse arguments
    CommandLine cl;
    //noinspection Duplicates
    try {
      cl = new DefaultParser().parse(options, args);
    } catch (ParseException e) {
      System.err.println("Parsing failed.  Reason: " + e.getMessage());
      formatter.printHelp(USAGE, options);
      return;
    }

    if (!commandLineCheck(cl, formatter, options)) {
      return;
    }

    // Get endpoint name
    String endpoint = getEndpointName(cl);

    // Get server URI
    String serverURI = getServerUrl(cl);

    // get PSK info
    byte[] pskIdentity = getPskIdentity(cl);
    byte[] pskKey = getPskKey(cl);

    // get RPK info
    PublicKey clientPublicKey = null;
    PrivateKey clientPrivateKey = null;
    PublicKey serverPublicKey = null;
    if (cl.hasOption("cpubk")) {
      try {
        clientPrivateKey = SecurityUtil.extractPrivateKey(cl.getOptionValue("cprik"));
        clientPublicKey = SecurityUtil.extractPublicKey(cl.getOptionValue("cpubk"));
        serverPublicKey = SecurityUtil.extractPublicKey(cl.getOptionValue("spubk"));
      } catch (Exception e) {
        System.err.println("Unable to load RPK files : " + e.getMessage());
        e.printStackTrace();
        formatter.printHelp(USAGE, options);
        return;
      }
    }

    // get local address
    String localAddress = getLocalAddress(cl);
    int localPort = getLocalPort(cl);

    Float latitude = null;
    Float longitude = null;
    float scaleFactor = 1.0f;

    // get initial Location
    if (cl.hasOption("pos")) {
      try {
        String pos = cl.getOptionValue("pos");
        int colon = pos.indexOf(':');
        if (colon == -1 || colon == 0 || colon == pos.length() - 1) {
          System.err.println(
              "Position must be a set of two floats separated by a colon, e.g. 48.131:11.459");
          formatter.printHelp(USAGE, options);
          return;
        }
        latitude = Float.valueOf(pos.substring(0, colon));
        longitude = Float.valueOf(pos.substring(colon + 1));
      } catch (NumberFormatException e) {
        System.err.println(
            "Position must be a set of two floats separated by a colon, e.g. 48.131:11.459");
        formatter.printHelp(USAGE, options);
        return;
      }
    }
    if (cl.hasOption("sf")) {
      try {
        scaleFactor = Float.parseFloat(cl.getOptionValue("sf"));
      } catch (NumberFormatException e) {
        System.err.println("Scale factor must be a float, e.g. 1.0 or 0.01");
        formatter.printHelp(USAGE, options);
        return;
      }
    }

    createAndStartClient(endpoint, localAddress, localPort, cl.hasOption("b"), serverURI,
        pskIdentity, pskKey, clientPublicKey, clientPrivateKey, serverPublicKey, latitude,
        longitude, scaleFactor);
  }

  private static void createAndStartClient(String endpoint, String localAddress, int localPort,
      boolean needBootstrap, String serverURI, byte[] pskIdentity, byte[] pskKey,
      PublicKey clientPublicKey, PrivateKey clientPrivateKey, PublicKey serverPublicKey,
      Float latitude, Float longitude, float scaleFactor) {

    MyLocation locationInstance = new MyLocation(latitude, longitude, scaleFactor);

    // Initialize model
    List<ObjectModel> models = getModels();

    // Initialize object list
    List<LwM2mObjectEnabler> enablers = getEnablers(models, needBootstrap, pskIdentity, serverURI,
        pskKey, clientPublicKey, clientPrivateKey, serverPublicKey, locationInstance);

    // Create CoAP Config
    NetworkConfig coapConfig = createCoapConfig();

    final LeshanClient client = getClient(endpoint, localAddress, localPort, enablers, coapConfig);

    // Display client public key to easily add it in Leshan Server Demo
    printClientPublicKey(clientPublicKey, clientPrivateKey);

    LOG.info("Press 'w','a','s','d' to change reported Location ({},{}).",
        locationInstance.getLatitude(),
        locationInstance.getLongitude());

    // Start the client
    client.start();

    // De-register on shutdown and stop client.
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        client.destroy(true); // send de-registration request before destroy
      }
    });

    // Change the location through the Console
    try (Scanner scanner = new Scanner(System.in)) {
      while (scanner.hasNext()) {
        String nextMove = scanner.next();
        locationInstance.moveLocation(nextMove);
      }
    }
  }

  private static Options defineCommandLineOptions() {
    Options options = new Options();

    options.addOption("h", "help", false, "Display help information.");
    options.addOption("n", true, String
        .format("Set the endpoint name of the Client.\nDefault: the local hostname or '%s' if any.",
            DEFAULT_ENDPOINT));
    options.addOption("b", false, "If present use bootstrap.");
    options.addOption("lh", true,
        "Set the local CoAP address of the Client.\n  Default: any local address.");
    options.addOption("lp", true,
        "Set the local CoAP port of the Client.\n  Default: A valid port value is between 0 and 65535.");
    options.addOption("u", true, String
        .format("Set the LWM2M or Bootstrap server URL.\nDefault: localhost:%d.",
            LwM2m.DEFAULT_COAP_PORT));
    options.addOption("pos", true,
        "Set the initial location (latitude, longitude) of the device to be reported by the Location object.\n Format: lat_float:long_float");
    options.addOption("sf", true,
        "Scale factor to apply when shifting position.\n Default is 1.0." + getPSKChapter());
    options.addOption("i", true, "Set the LWM2M or Bootstrap server PSK identity in ascii.");
    options.addOption("p", true,
        "Set the LWM2M or Bootstrap server Pre-Shared-Key in hexa." + getRPKChapter());
    options.addOption("cpubk", true,
        "The path to your client public key file.\n The public Key should be in SubjectPublicKeyInfo format (DER encoding).");
    options.addOption("cprik", true,
        "The path to your client private key file.\nThe private key should be in PKCS#8 format (DER encoding).");
    options.addOption("spubk", true,
        "The path to your server public key file.\n The public Key should be in SubjectPublicKeyInfo format (DER encoding).");

    return options;
  }

  private static StringBuilder getPSKChapter() {
    final StringBuilder PSKChapter = new StringBuilder();

    PSKChapter.append("\n .");
    PSKChapter.append("\n .");
    PSKChapter
        .append("\n ================================[ PSK ]=================================");
    PSKChapter
        .append("\n | By default Leshan demo use non secure connection.                    |");
    PSKChapter
        .append("\n | To use PSK, -i and -p options should be used together.               |");
    PSKChapter
        .append("\n ------------------------------------------------------------------------");

    return PSKChapter;
  }

  private static StringBuilder getRPKChapter() {
    final StringBuilder RPKChapter = new StringBuilder();

    RPKChapter.append("\n .");
    RPKChapter.append("\n .");
    RPKChapter
        .append("\n ================================[ RPK ]=================================");
    RPKChapter
        .append("\n | By default Leshan demo use non secure connection.                    |");
    RPKChapter
        .append("\n | To use RPK, -cpubk -cpribk -spubk options should be used together.   |");
    RPKChapter
        .append("\n | To get helps about files format and how to generate it, see :        |");
    RPKChapter
        .append("\n | See https://github.com/eclipse/leshan/wiki/Credential-files-format   |");
    RPKChapter
        .append("\n ------------------------------------------------------------------------");

    return RPKChapter;
  }

  private static boolean commandLineCheck(CommandLine cl, HelpFormatter formatter,
      Options options) {
    // Print help
    if (cl.hasOption("help")) {
      formatter.printHelp(USAGE, options);
      return false;
    }

    // Abort if unexpected options
    if (cl.getArgs().length > 0) {
      System.err.println("Unexpected option or arguments : " + cl.getArgList());
      formatter.printHelp(USAGE, options);
      return false;
    }

    // Abort if PSK config is not complete
    if ((cl.hasOption("i") && !cl.hasOption("p")) || !cl.hasOption("i") && cl.hasOption("p")) {
      System.err
          .println(
              "You should precise identity (-i) and Pre-Shared-Key (-p) if you want to connect in PSK");
      formatter.printHelp(USAGE, options);
      return false;
    }

    // Abort if all RPK config is not complete
    if (cl.hasOption("cpubk") || cl.hasOption("cprik") || cl.hasOption("spubk")) {
      if (!cl.hasOption("cpubk") || !cl.hasOption("cprik") || !cl.hasOption("spubk")) {
        System.err.println("cpubk, cprik and spubk should be used together to connect using RPK");
        formatter.printHelp(USAGE, options);
        return false;
      }
    }

    return true;
  }

  private static String getEndpointName(CommandLine cl) {
    String endpoint;

    if (cl.hasOption("n")) {
      endpoint = cl.getOptionValue("n");
    } else {
      try {
        endpoint = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        endpoint = DEFAULT_ENDPOINT;
      }
    }

    return endpoint;
  }

  private static String getServerUrl(CommandLine cl) {
    String serverURI;

    if (cl.hasOption("u")) {
      if (cl.hasOption("i") || cl.hasOption("cpubk")) {
        serverURI = "coaps://" + cl.getOptionValue("u");
      } else {
        serverURI = "coap://" + cl.getOptionValue("u");
      }
    } else {
      if (cl.hasOption("i") || cl.hasOption("cpubk")) {
        serverURI = "coaps://localhost:" + LwM2m.DEFAULT_COAP_SECURE_PORT;
      } else {
        serverURI = "coap://localhost:" + LwM2m.DEFAULT_COAP_PORT;
      }
    }

    return serverURI;
  }

  private static byte[] getPskIdentity(CommandLine cl) {
    byte[] pskIdentity = null;

    if (cl.hasOption("i")) {
      pskIdentity = cl.getOptionValue("i").getBytes();
    }

    return pskIdentity;
  }

  private static byte[] getPskKey(CommandLine cl) {
    byte[] pskKey = null;

    if (cl.hasOption("i")) {
      pskKey = Hex.decodeHex(cl.getOptionValue("p").toCharArray());
    }

    return pskKey;
  }

  private static String getLocalAddress(CommandLine cl) {
    String localAddress = null;

    if (cl.hasOption("lh")) {
      localAddress = cl.getOptionValue("lh");
    }

    return localAddress;
  }

  private static int getLocalPort(CommandLine cl) {
    int localPort = 0;

    if (cl.hasOption("lp")) {
      localPort = Integer.parseInt(cl.getOptionValue("lp"));
    }

    return localPort;
  }

  private static List<ObjectModel> getModels() {
    List<ObjectModel> models = ObjectLoader.loadDefault();
    models.addAll(ObjectLoader.loadDdfResources("/models", modelPaths));

    return models;
  }

  private static ObjectsInitializer getInitializer(List<ObjectModel> models, boolean needBootstrap,
      byte[] pskIdentity, String serverURI, byte[] pskKey, PublicKey clientPublicKey,
      PrivateKey clientPrivateKey, PublicKey serverPublicKey, MyLocation locationInstance) {

    ObjectsInitializer initializer = new ObjectsInitializer(new LwM2mModel(models));
    if (needBootstrap) {
      if (pskIdentity != null) {
        initializer.setInstancesForObject(SECURITY, pskBootstrap(serverURI, pskIdentity, pskKey));
        initializer.setClassForObject(SERVER, Server.class);
      } else if (clientPublicKey != null) {
        initializer
            .setInstancesForObject(SECURITY, rpkBootstrap(serverURI, clientPublicKey.getEncoded(),
                clientPrivateKey.getEncoded(), serverPublicKey.getEncoded()));
        initializer.setClassForObject(SERVER, Server.class);
      } else {
        initializer.setInstancesForObject(SECURITY, noSecBootstap(serverURI));
        initializer.setClassForObject(SERVER, Server.class);
      }
    } else {
      if (pskIdentity != null) {
        initializer.setInstancesForObject(SECURITY, psk(serverURI, 123, pskIdentity, pskKey));
        initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
      } else if (clientPublicKey != null) {
        initializer
            .setInstancesForObject(SECURITY, rpk(serverURI, 123, clientPublicKey.getEncoded(),
                clientPrivateKey.getEncoded(), serverPublicKey.getEncoded()));
        initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
      } else {
        initializer.setInstancesForObject(SECURITY, noSec(serverURI, 123));
        initializer.setInstancesForObject(SERVER, new Server(123, 30, BindingMode.U, false));
      }
    }

    // ----------------------------- Initialise devices --------------------------------------------
    initializer.setClassForObject(DEVICE, MyDevice.class);
    initializer.setInstancesForObject(LOCATION, locationInstance);
    initializer.setInstancesForObject(OBJECT_ID_TEMPERATURE_SENSOR, new RandomTemperatureSensor());
    initializer.setInstancesForObject(OBJECT_ID_WATER_FLOW_SENSOR, new WaterFlowSensor());
    initializer.setInstancesForObject(OBJECT_ID_HUMIDITY_SENSOR, new HumiditySensor());
    // ---------------------------------------------------------------------------------------------
    return initializer;
  }

  private static List<LwM2mObjectEnabler> getEnablers(List<ObjectModel> models,
      boolean needBootstrap, byte[] pskIdentity, String serverURI, byte[] pskKey,
      PublicKey clientPublicKey, PrivateKey clientPrivateKey, PublicKey serverPublicKey,
      MyLocation locationInstance) {

    ObjectsInitializer initializer = getInitializer(models, needBootstrap, pskIdentity, serverURI,
        pskKey, clientPublicKey, clientPrivateKey, serverPublicKey, locationInstance);

    // ---------------------------------- Create devices -------------------------------------------
    return initializer.create(
        SECURITY, SERVER, DEVICE, LOCATION, OBJECT_ID_TEMPERATURE_SENSOR,
        OBJECT_ID_WATER_FLOW_SENSOR, OBJECT_ID_HUMIDITY_SENSOR
    );
    // ---------------------------------------------------------------------------------------------
  }

  private static NetworkConfig createCoapConfig() {
    NetworkConfig coapConfig;
    File configFile = new File(NetworkConfig.DEFAULT_FILE_NAME);
    if (configFile.isFile()) {
      coapConfig = new NetworkConfig();
      coapConfig.load(configFile);
    } else {
      coapConfig = LeshanClientBuilder.createDefaultNetworkConfig();
      coapConfig.store(configFile);
    }

    return coapConfig;
  }

  private static LeshanClient getClient(String endpoint, String localAddress, int localPort,
      List<LwM2mObjectEnabler> enablers, NetworkConfig coapConfig) {

    LeshanClientBuilder builder = new LeshanClientBuilder(endpoint);
    builder.setLocalAddress(localAddress, localPort);
    builder.setObjects(enablers);
    builder.setCoapConfig(coapConfig);

    return builder.build();
  }

  private static void printClientPublicKey(PublicKey clientPublicKey, PrivateKey clientPrivateKey) {
    // Display client public key to easily add it in Leshan Server Demo
    if (clientPublicKey != null) {
      if (clientPublicKey instanceof ECPublicKey) {
        ECPublicKey ecPublicKey = (ECPublicKey) clientPublicKey;
        // Get x coordinate
        byte[] x = ecPublicKey.getW().getAffineX().toByteArray();
        if (x[0] == 0) {
          x = Arrays.copyOfRange(x, 1, x.length);
        }

        // Get Y coordinate
        byte[] y = ecPublicKey.getW().getAffineY().toByteArray();
        if (y[0] == 0) {
          y = Arrays.copyOfRange(y, 1, y.length);
        }

        // Get Curves params
        String params = ecPublicKey.getParams().toString();

        LOG.info(
            "Client uses RPK : \n" +
            "Elliptic Curve parameters  : {} \n" +
            "Public x coord : {} \n" +
            "Public y coord : {} \n" +
            "Public Key (Hex): {} \n" +
            "Private Key (Hex): {}",
            params, Hex.encodeHexString(x), Hex.encodeHexString(y),
            Hex.encodeHexString(clientPublicKey.getEncoded()),
            Hex.encodeHexString(clientPrivateKey.getEncoded())
        );

      } else {
        throw new IllegalStateException(
            "Unsupported Public Key Format (only ECPublicKey supported).");
      }
    }
  }
}
