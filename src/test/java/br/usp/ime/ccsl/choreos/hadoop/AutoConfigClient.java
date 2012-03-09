package br.usp.ime.ccsl.choreos.hadoop;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.ext.xml.XMLSource;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import static org.junit.Assert.*;
import org.junit.BeforeClass;
import org.junit.Test;

public class AutoConfigClient {

	@BeforeClass
	public static void testServerUp() throws Exception {
		try {
			WebClient wc = WebClient.create(HadoopWSServer.SERVER_ADDRESS
					+ "hadoop");

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
			assertEquals("hdfs://aguia1.ime.usp.br:54310", dfs.getValue());

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
			
			FileSystem fs = FileSystem.get(conf);
			
			try {
				fs.mkdirs(new Path("/test"));
			}
			catch(IOException ioe) {
				ioe.printStackTrace();
				fail("Could not create a directory '/test'");
			}
			
			FSDataOutputStream out = fs.create(new Path("/test/test.txt"));

			out.writeUTF("1\tthis is a test line content");
			
			out.close();
			

		} finally {
			httpclient.getConnectionManager().shutdown();
		}
	}

	private String retrieveConfig(HttpClient httpclient) throws IOException,
			ClientProtocolException {
		HttpGet httpget = new HttpGet(HadoopWSServer.SERVER_ADDRESS + "hadoop");

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