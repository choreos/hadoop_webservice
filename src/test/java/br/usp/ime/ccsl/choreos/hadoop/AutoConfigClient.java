package br.usp.ime.ccsl.choreos.hadoop;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.xml.XMLSource;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.BeforeClass;
import org.junit.Test;

public class AutoConfigClient {

	// URL configuration
	// private static String URL = "http://143.107.45.126:8080/hadoop";
	private static String URL = "http://localhost:8080/hadoop";
	// private static String URL = HadoopWSServer.SERVER_ADDRESS + "hadoop";

	// private static String DFS_URL = "hdfs://143.107.45.126:54310";
	private static String DFS_URL = "hdfs://localhost:54310";
	private static String MAPRED_URL = "localhost:54311";
	private static String PROJECT_PATH = System.getProperty("user.home")
			+ File.separator + "choreos_middleware" + File.separator
			+ "hadoop_webservice";

	@BeforeClass
	public static void testServerUp() throws Exception {
		try {
			WebClient wc = WebClient.create(URL);

			wc.get(XMLSource.class);
		} catch (Exception e) {
			throw new Exception("connection error");
		}
	}

	@Test
	public void testRetrieveConfigXML() throws ClientProtocolException,
			IOException {
		HttpClient httpclient = new DefaultHttpClient();
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(
					retrieveConfig(httpclient).getBytes());
			XMLSource xml = new XMLSource(bais);
			xml.setBuffering(true);

			Property dfs = xml.getNode("/property[name='fs.defaultFS']",
					Property.class);
			assertEquals(DFS_URL, dfs.getValue());

		} finally {
			httpclient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testSetUpHadoopClient() throws ClientProtocolException,
			IOException {
		HttpClient httpclient = new DefaultHttpClient();
		try {

			Configuration conf = new Configuration(true);

			String oldValue = conf.get("fs.defaultFS");
			System.out.println("old=" + oldValue);

			ByteArrayInputStream bais = new ByteArrayInputStream(
					retrieveConfig(httpclient).getBytes());
			XMLSource xml = new XMLSource(bais);
			xml.setBuffering(true);

			Property dfs = xml.getNode("/property[name='fs.defaultFS']",
					Property.class);
			String newValue = dfs.getValue();

			System.out.println("new=" + newValue);

			assertFalse("old and new values are different",
					newValue.equals(oldValue));

			conf.set("fs.defaultFS", newValue);

			assertEquals(newValue, conf.get("fs.defaultFS"));

		} finally {
			httpclient.getConnectionManager().shutdown();
		}

	}

	@Test
	public void acessingHadoopFS() throws ClientProtocolException, IOException {
		HttpClient httpclient = new DefaultHttpClient();
		FileSystem fs = null;
		try {

			Configuration conf = new Configuration(true);

			ByteArrayInputStream bais = new ByteArrayInputStream(
					retrieveConfig(httpclient).getBytes());
			XMLSource xml = new XMLSource(bais);
			xml.setBuffering(true);

			Property dfs = xml.getNode("/property[name='fs.defaultFS']",
					Property.class);
			String newValue = dfs.getValue();

			conf.set("fs.defaultFS", newValue);

			fs = FileSystem.get(conf);

			try {
				fs.mkdirs(new Path("/test"));
			} catch (IOException ioe) {
				ioe.printStackTrace();
				fail("Could not create a directory '/test'");
			}

			FSDataOutputStream out = null;
			try {
				out = fs.create(new Path("/test/test.txt"));

				out.writeUTF("1\tthis is a test line content");
				out.flush();
			} finally {
				out.close();
			}

			fs.deleteOnExit(new Path("/test/test.txt"));
			fs.deleteOnExit(new Path("/test"));

		} finally {
			fs.close();
			FileSystem.closeAll();
			httpclient.getConnectionManager().shutdown();
		}
	}

	@Test
	public void testWordCount() throws Exception {
		HttpClient httpclient = new DefaultHttpClient();
		FileSystem fs = null;
		try {

			Configuration conf = new Configuration(true);

			ByteArrayInputStream bais = new ByteArrayInputStream(
					retrieveConfig(httpclient).getBytes());
			XMLSource xml = new XMLSource(bais);
			xml.setBuffering(true);

			Property dfs = xml.getNode("/property[name='fs.defaultFS']",
					Property.class);
			String newValue = dfs.getValue();

			System.out.println("fs.defaultFS=" + newValue);
			conf.set("fs.defaultFS", newValue);
			conf.set("mapred.job.tracker", MAPRED_URL);

			fs = FileSystem.get(conf);

			try {
				fs.mkdirs(new Path("/input"));
			} catch (IOException ioe) {
				ioe.printStackTrace();
				fail("Could not create a directory '/test'");
			}

			FSDataOutputStream output = fs.create(new Path("/input/test.txt"));
			FileInputStream input = new FileInputStream(new File(PROJECT_PATH
					+ "/src/test/resource/test.txt"));

			try {
				int read = 0;

				while ((read = input.read()) != -1) {
					output.write(read);
				}
			} finally {
				output.close();
				input.close();
			}

			WordCount.setConf(conf);
			WordCount.main(null);

			assertTrue(fs.exists(new Path("/output/SUCCESS")));

		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			fs.close();
			FileSystem.closeAll();
			httpclient.getConnectionManager().shutdown();
		}
	}

	private String retrieveConfig(HttpClient httpclient) throws IOException,
			ClientProtocolException {
		HttpGet httpget = new HttpGet(URL);

		System.out.println("executing request " + httpget.getURI());

		ResponseHandler<String> responseHandler = new BasicResponseHandler();
		String responseBody = httpclient.execute(httpget, responseHandler);

		return responseBody;
	}

	static class Property {

		private String name;
		private String value;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}
	};


}
