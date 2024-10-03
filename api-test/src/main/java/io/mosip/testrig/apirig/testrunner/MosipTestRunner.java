package io.mosip.testrig.apirig.testrunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.testng.TestNG;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.mosip.testrig.apirig.dataprovider.BiometricDataProvider;
import io.mosip.testrig.apirig.dbaccess.DBManager;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.CertificateGenerationUtil;
import io.mosip.testrig.apirig.utils.CertsUtil;
import io.mosip.testrig.apirig.utils.ConfigManager;
import io.mosip.testrig.apirig.utils.EncryptionDecrptionUtil;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.IdRepoConfigManager;
import io.mosip.testrig.apirig.utils.JWKKeyUtil;
import io.mosip.testrig.apirig.utils.KeyCloakUserAndAPIKeyGeneration;
import io.mosip.testrig.apirig.utils.KeycloakUserManager;
import io.mosip.testrig.apirig.utils.MispPartnerAndLicenseKeyGeneration;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.PartnerRegistration;
import io.mosip.testrig.apirig.utils.SkipTestCaseHandler;

/**
 * Class to initiate mosip api test execution
 * 
 * @author Vignesh
 *
 */
public class MosipTestRunner {
	private static final Logger LOGGER = Logger.getLogger(MosipTestRunner.class);
	private static String cachedPath = null;

	public static String jarUrl = MosipTestRunner.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	public static List<String> languageList = new ArrayList<>();

	/**
	 * C Main method to start mosip test execution
	 * 
	 * @param arg
	 */
	public static void main(String[] arg) {

		try {

			Map<String, String> envMap = System.getenv();
			LOGGER.info("** ------------- Get ALL ENV varibales --------------------------------------------- **");
			for (String envName : envMap.keySet()) {
				LOGGER.info(String.format("ENV %s = %s%n", envName, envMap.get(envName)));
			}
			ExtractResource.removeOldMosipTestTestResource();
			if (getRunType().equalsIgnoreCase("JAR")) {
				ExtractResource.extractCommonResourceFromJar();
			} else {
				ExtractResource.copyCommonResources();
			}
			IdRepoConfigManager.init();
			BaseTestCase.suiteSetup(getRunType());
			SkipTestCaseHandler.loadTestcaseToBeSkippedList("testCaseSkippedList.txt");
			setLogLevels();

			// For now we are not doing health check for qa-115.
			if (BaseTestCase.isTargetEnvLTS()) {
				HealthChecker healthcheck = new HealthChecker();
				healthcheck.setCurrentRunningModule(BaseTestCase.currentModule);
				Thread trigger = new Thread(healthcheck);
				trigger.start();
			}
			KeycloakUserManager.removeUser();
			KeycloakUserManager.createUsers();
			KeycloakUserManager.closeKeycloakInstance();

			List<String> localLanguageList = new ArrayList<>(BaseTestCase.getLanguageList());
			AdminTestUtil.getLocationData();
			
			// Generate device certificates to be consumed by Mock-MDS
			PartnerRegistration.deleteCertificates();
			AdminTestUtil.createAndPublishPolicy();
			AdminTestUtil.createEditAndPublishPolicy();
			PartnerRegistration.deviceGeneration();

			BiometricDataProvider.generateBiometricTestData("Registration");

			startTestRunner();
		} catch (Exception e) {
			LOGGER.error("Exception " + e.getMessage());
		}

		OTPListener.bTerminate = true;

		if (BaseTestCase.isTargetEnvLTS())
			HealthChecker.bTerminate = true;

		System.exit(0);

	}

	private static void setLogLevels() {
		AdminTestUtil.setLogLevel();
		OutputValidationUtil.setLogLevel();
		PartnerRegistration.setLogLevel();
		KeyCloakUserAndAPIKeyGeneration.setLogLevel();
		MispPartnerAndLicenseKeyGeneration.setLogLevel();
		JWKKeyUtil.setLogLevel();
		CertsUtil.setLogLevel();
	}

	/**
	 * The method to start mosip testng execution
	 * 
	 * @throws IOException
	 */
	public static void startTestRunner() {
		File homeDir = null;
		TestNG runner = new TestNG();
		List<String> suitefiles = new ArrayList<>();
		List<String> modulesToRun = BaseTestCase.listOfModules;
		String os = System.getProperty("os.name");
		LOGGER.info(os);
		if (getRunType().contains("IDE") || os.toLowerCase().contains("windows")) {
			homeDir = new File(System.getProperty("user.dir") + "/testNgXmlFiles");
			LOGGER.info("IDE :" + homeDir);
		} else {
			File dir = new File(System.getProperty("user.dir"));
			homeDir = new File(dir.getParent() + "/mosip/testNgXmlFiles");
			LOGGER.info("ELSE :" + homeDir);
		}
		for (File file : homeDir.listFiles()) {
			for (String fileName : modulesToRun) {
				if (file.getName().toLowerCase().contains(fileName)) {
					suitefiles.add(file.getAbsolutePath());
				} else if (fileName.equals("all") && file.getName().toLowerCase().contains("testng")) {
					suitefiles.add(file.getAbsolutePath());
				}
			}
		}
		runner.setTestSuites(suitefiles);
		System.getProperties().setProperty("testng.outpur.dir", "testng-report");
		runner.setOutputDirectory("testng-report");
		runner.run();
	}

	/**
	 * The method to return class loader resource path
	 * 
	 * @return String
	 * @throws IOException
	 */
	/*
	 * public static String getGlobalResourcePath() { if
	 * (getRunType().equalsIgnoreCase("JAR")) { return new
	 * File(jarUrl).getParentFile().getAbsolutePath() +
	 * "/MosipTestResource/MosipTemporaryTestResource"; } else if
	 * (getRunType().equalsIgnoreCase("IDE")) { String path = new
	 * File(MosipTestRunner.class.getClassLoader().getResource("").getPath()).
	 * getAbsolutePath() + "/MosipTestResource/MosipTemporaryTestResource"; if
	 * (path.contains(GlobalConstants.TESTCLASSES)) path =
	 * path.replace(GlobalConstants.TESTCLASSES, "classes"); return path; } return
	 * "Global Resource File Path Not Found"; }
	 */

	public static String getGlobalResourcePath() {
		if (cachedPath != null) {
			return cachedPath;
		}

		String path = null;
		if (getRunType().equalsIgnoreCase("JAR")) {
			path = new File(jarUrl).getParentFile().getAbsolutePath() + "/MosipTestResource/MosipTemporaryTestResource";
		} else if (getRunType().equalsIgnoreCase("IDE")) {
			path = new File(MosipTestRunner.class.getClassLoader().getResource("").getPath()).getAbsolutePath()
					+ "/MosipTestResource/MosipTemporaryTestResource";
			if (path.contains(GlobalConstants.TESTCLASSES))
				path = path.replace(GlobalConstants.TESTCLASSES, "classes");
		}

		if (path != null) {
			cachedPath = path;
			return path;
		} else {
			return "Global Resource File Path Not Found";
		}
	}

	public static String getResourcePath() {
		return getGlobalResourcePath();
//		if (getRunType().equalsIgnoreCase("JAR")) {
//			return new File(jarUrl).getParentFile().getAbsolutePath();
//		} else if (getRunType().equalsIgnoreCase("IDE")) {
//			String path = new File(MosipTestRunner.class.getClassLoader().getResource("").getPath()).getAbsolutePath();
//			if (path.contains(GlobalConstants.TESTCLASSES))
//				path = path.replace(GlobalConstants.TESTCLASSES, "classes");
//			return path;
//		}
//		return "Global Resource File Path Not Found";
	}

	public static String generatePulicKey() {
		String publicKey = null;
		try {
			KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
			keyGenerator.initialize(2048, BaseTestCase.secureRandom);
			final KeyPair keypair = keyGenerator.generateKeyPair();
			publicKey = java.util.Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded());
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error(e.getMessage());
		}
		return publicKey;
	}

	public static KeyPairGenerator keyPairGen = null;

	public static KeyPairGenerator getKeyPairGeneratorInstance() {
		if (keyPairGen != null)
			return keyPairGen;
		try {
			keyPairGen = KeyPairGenerator.getInstance("RSA");
			keyPairGen.initialize(2048);

		} catch (NoSuchAlgorithmException e) {
			LOGGER.error(e.getMessage());
		}

		return keyPairGen;
	}

	public static String generatePublicKeyForMimoto() {

		String vcString = "";
		try {
			KeyPairGenerator keyPairGenerator = getKeyPairGeneratorInstance();
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			PublicKey publicKey = keyPair.getPublic();
			StringWriter stringWriter = new StringWriter();
			try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
				pemWriter.writeObject(publicKey);
				pemWriter.flush();
				vcString = stringWriter.toString();
				if (System.getProperty("os.name").toLowerCase().contains("windows")) {
					vcString = vcString.replaceAll("\r\n", "\\\\n");
				} else {
					vcString = vcString.replaceAll("\n", "\\\\n");
				}
			} catch (Exception e) {
				throw e;
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
		return vcString;
	}

	public static String generateJWKPublicKey() {
		try {
			KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
			keyGenerator.initialize(2048, BaseTestCase.secureRandom);
			final KeyPair keypair = keyGenerator.generateKeyPair();
			RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keypair.getPublic()).keyID("RSAKeyID")
					.keyUse(KeyUse.SIGNATURE).privateKey(keypair.getPrivate()).build();

			return jwk.toJSONString();
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
	}

	public static Properties getproperty(String path) {
		Properties prop = new Properties();
		FileInputStream inputStream = null;
		try {
			File file = new File(path);
			inputStream = new FileInputStream(file);
			prop.load(inputStream);
		} catch (Exception e) {
			LOGGER.error(GlobalConstants.EXCEPTION_STRING_2 + e.getMessage());
		} finally {
			AdminTestUtil.closeInputStream(inputStream);
		}
		return prop;
	}

	/**
	 * The method will return mode of application started either from jar or eclipse
	 * ide
	 * 
	 * @return
	 */
	public static String getRunType() {
		if (MosipTestRunner.class.getResource("MosipTestRunner.class").getPath().contains(".jar"))
			return "JAR";
		else
			return "IDE";
	}

}
