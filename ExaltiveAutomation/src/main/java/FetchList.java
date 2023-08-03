import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.bonigarcia.wdm.WebDriverManager;

public class FetchList {

	private static final String GET_URL = "https://prod.exaltive.com/api/exlative/geturls";
	private static WebDriver driver;
	private static String path;
	private static Logger logger = Logger.getLogger(FetchList.class);

	private static void getUrls() throws IOException, InterruptedException {
		URL obj = new URL(GET_URL);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		int responseCode = con.getResponseCode();
		System.out.println("GET Response Code :: " + responseCode);
		if (responseCode == HttpURLConnection.HTTP_OK) { // success
			BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
			String inputLine;
			StringBuffer response = new StringBuffer();

			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
			in.close();

			// print result
			JSONArray jsonObjects = new JSONArray(response.toString());

			for (int i = 0; i < jsonObjects.length(); i++) { // iteration
				JSONObject tmp = jsonObjects.getJSONObject(i);
				launchAppAndDownloadVideo(tmp.getString("encryptedlink"), i);
				// driver.get(tmp.getString("encryptedlink"));
				System.out.println(tmp.getString("encryptedlink"));
				System.out.println(tmp.getDouble("duration"));
			}

		} else {
			System.out.println("GET request did not work.");
		}

	}

	private static void launchChrome() throws Exception {
		WebDriverManager.chromedriver().setup();

		// get download path
		String strDownloadpath = getDownloadPath();

		HashMap<String, Object> chromePrefs = new HashMap<>();
		chromePrefs.put("download.default_directory", strDownloadpath);
		chromePrefs.put("profile.default_content_settings.popups", 0);
		chromePrefs.put("download.prompt_for_download", false);
		chromePrefs.put("credentials_enable_service", false);
		chromePrefs.put("safebrowsing.enabled", true);
		chromePrefs.put("profile.password_manager_enabled", false);

		// ChromeOptions
		ChromeOptions options = new ChromeOptions();
		options.setCapability(CapabilityType.ACCEPT_INSECURE_CERTS, true);
		options.setExperimentalOption("prefs", chromePrefs);
		options.setExperimentalOption("excludeSwitches", new String[] { "enable-automation", "load-extension" });

		driver = new ChromeDriver(options);
		driver.manage().window().maximize();
		// driver.navigate().to(AppURL);

	}

	private static String getDownloadPath() {
		String strDownloadpath = System.getProperty("user.dir");
		String relativepathdownload = "\\Download\\";
		strDownloadpath = strDownloadpath + relativepathdownload;
		return strDownloadpath;
	}

	private static void launchAppAndDownloadVideo(String AppURL, int loop) throws InterruptedException, IOException {
		driver.navigate().to(AppURL);
		Thread.sleep(5 * 1000);
		WebElement downloadBtn = new WebDriverWait(driver, Duration.ofSeconds(10))
				.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[@id='start']")));
		By videoPlayer = By.xpath("//div[@id='PlayerContainer']");
		if (isElementPresent(driver, videoPlayer)) {
			driver.findElement(By.xpath("//button[@id='start']")).click();
			Thread.sleep(40000);
			// create a process and execute ShareExaltivVideo.exe

			Runtime.getRuntime().exec(path + "\\src\\main\\resources\\ShareExaltivVideo.exe");
			Thread.sleep(60000);
			System.out.println("Loop:" + loop);
			movedFilesToServer();

		} else {
			logger.error("Content is not Present for " + AppURL + " Url");
		}

	}

	private static void movedFilesToServer() throws IOException, InterruptedException {

		String strDownloadpath = getDownloadPath();
		File getLatestFile = getLatestFilefromDir(strDownloadpath);	

		// check file is exist or not

		if (getLatestFile!=null && getLatestFile.exists()) {
			
			String path = getLatestFile.getPath();

			String copiedDownloadpath = System.getProperty("user.dir");
			String relativeCopiedpathdownload = "\\Destination\\";
			copiedDownloadpath = copiedDownloadpath + relativeCopiedpathdownload;

			String destFilePath = copiedDownloadpath + File.separator + getLatestFile.getName();
			File destFile = new File(destFilePath);
			if (!destFile.exists()) {
				FileUtils.moveFileToDirectory(getLatestFile, new File(copiedDownloadpath), false);
			} else {
				logger.error("file is already available on the server");
				Files.delete(Paths.get(path));
			}

		}

	}

	private static File getLatestFilefromDir(String dirPath) {
		File dir = new File(dirPath);
		File[] files = dir.listFiles();
		if (files == null || files.length == 0) {
			return null;
		}

		File lastModifiedFile = files[0];
		for (int i = 1; i < files.length; i++) {
			if (lastModifiedFile.lastModified() < files[i].lastModified()) {
				lastModifiedFile = files[i];
			}
		}
		return lastModifiedFile;
	}

	private static boolean isElementPresent(WebDriver driver, By by) {
		return driver.findElements(by).stream().count() != 0;

	}

	public static void main(String[] args) throws Exception {
		BasicConfigurator.configure();
		generateLogFile();
		path = System.getProperty("user.dir");
		System.out.println(path);
		launchChrome();
		getUrls();

	}

	private static void generateLogFile() {
		// creates pattern layout
		PatternLayout layout = new PatternLayout();
		String conversionPattern = "[%p] %d %c %M - %m%n";
		layout.setConversionPattern(conversionPattern);

		// creates daily rolling file appender
		DailyRollingFileAppender rollingAppender = new DailyRollingFileAppender();
		rollingAppender.setFile("AutomationLogs/ExaltiveAutomation");
		rollingAppender.setDatePattern("'_'yyyy_MM_dd'.log'");
		rollingAppender.setLayout(layout);
		rollingAppender.activateOptions();

		// configures the root logger
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.ERROR);
		rootLogger.addAppender(rollingAppender);

	}

}
