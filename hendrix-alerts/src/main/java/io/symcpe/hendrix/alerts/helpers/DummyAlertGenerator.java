package io.symcpe.hendrix.alerts.helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;

import io.symcpe.wraith.actions.alerts.Alert;

public class DummyAlertGenerator {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		Gson gson = new Gson();
		PrintWriter pr = new PrintWriter(
//				new GZIPOutputStream(
						new FileOutputStream(new File("target/alerts.txt")
//								)
						));
		for(int i=0;i<10;i++) {
			Alert alert = new Alert();
			alert.setId((short)i);
			alert.setBody("test2");
			alert.setMedia("mail");
			alert.setSubject("hellow world");
			alert.setTarget("test@zyx.com");
			alert.setRuleGroup("34234234");
			alert.setTimestamp(System.currentTimeMillis());
			pr.println(gson.toJson(alert));
		}
		pr.close();
	}
	
}
