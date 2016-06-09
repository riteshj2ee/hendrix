/**
 * Copyright 2016 Symantec Corporation.
 * 
 * Licensed under the Apache License, Version 2.0 (the “License”); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.symcpe.hendrix.ui.rules;

import java.io.IOException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.primefaces.model.chart.Axis;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.DateAxis;
import org.primefaces.model.chart.LineChartModel;
import org.primefaces.model.chart.LineChartSeries;
import org.primefaces.model.chart.PieChartModel;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.symcpe.hendrix.ui.ApplicationManager;
import io.symcpe.hendrix.ui.UserBean;
import io.symcpe.hendrix.ui.alerts.Utils;

/**
 * JSF dashboard
 * 
 * @author ambud_sharma
 */
@ManagedBean(name = "dash")
@SessionScoped
public class Dashboard implements Serializable {

	private static final long serialVersionUID = 1L;
	private LineChartModel alertStats;
	private PieChartModel topRules;
	private LineChartModel ruleEfficiency;
	private SimpleDateFormat formatter = new SimpleDateFormat("YYYY-MM-dd HH:mm:ss");
	@ManagedProperty(value = "#{am}")
	private ApplicationManager am;
	@ManagedProperty(value = "#{ub}")
	private UserBean ub;

	public Dashboard() {
	}

	@PostConstruct
	public void init() {
		alertStats = new LineChartModel();
		initLinearModel();

		topRules = new PieChartModel();

		ruleEfficiency = new LineChartModel();
		ruleEfficiency.getAxis(AxisType.Y).setLabel("Execute Time (ms)");
	    
		initializeTopRules();
		initializeRuleEfficiency();
	}

	private void initializeTopRules() {
		topRules.clear();
		topRules.setLegendPosition("w");
	}

	private void initializeRuleEfficiency() {
		CloseableHttpClient client = Utils.getClient(am.getBaseUrl(), am.getConnectTimeout(), am.getRequestTimeout());
		HttpGet get = new HttpGet(am.getBaseUrl() + "/perf/" + ub.getTenant().getTenantId());
		Date date = null;
		try {
			CloseableHttpResponse response = client.execute(get);
			String string = EntityUtils.toString(response.getEntity());
			Gson gson = new Gson();
			JsonObject rules = gson.fromJson(string, JsonObject.class);
			for (Entry<String, JsonElement> entry : rules.entrySet()) {
				LineChartSeries series = new LineChartSeries(entry.getKey());
				
				for (JsonElement element : entry.getValue().getAsJsonArray()) {
					JsonObject point = element.getAsJsonObject();
					date = new Date(point.get("key").getAsLong());
					series.set(formatter.format(date), point.get("value").getAsNumber());
				}
				ruleEfficiency.addSeries(series);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		DateAxis axis = new DateAxis("Time");
	    axis.setTickAngle(-50);
	    axis.setMax(formatter.format(date));
	    axis.setTickFormat("%H:%#M:%S");
	    ruleEfficiency.getAxes().put(AxisType.X, axis);
	}

	private void initLinearModel() {
		alertStats.clear();
		LineChartSeries series1 = new LineChartSeries();
		series1.setLabel("Series 1");

		series1.set(1, 2);
		series1.set(2, 1);
		series1.set(3, 3);
		series1.set(4, 6);
		series1.set(5, 8);

		LineChartSeries series2 = new LineChartSeries();
		series2.setLabel("Series 2");

		series2.set(1, 6);
		series2.set(2, 3);
		series2.set(3, 2);
		series2.set(4, 7);
		series2.set(5, 9);

		alertStats.addSeries(series1);
		alertStats.addSeries(series2);

		alertStats.setLegendPosition("e");
		Axis yAxis = alertStats.getAxis(AxisType.Y);
		yAxis.setMin(0);
		yAxis.setMax(10);
	}

	/**
	 * @return the alertStats
	 */
	public LineChartModel getAlertStats() {
		return alertStats;
	}

	/**
	 * @return the topRules
	 */
	public PieChartModel getTopRules() {
		return topRules;
	}

	/**
	 * @return the ruleEfficiency
	 */
	public LineChartModel getRuleEfficiency() {
		return ruleEfficiency;
	}

	/**
	 * @return the ub
	 */
	public UserBean getUb() {
		return ub;
	}

	/**
	 * @param ub
	 *            the ub to set
	 */
	public void setUb(UserBean ub) {
		this.ub = ub;
	}

	/**
	 * @return the am
	 */
	public ApplicationManager getAm() {
		return am;
	}

	/**
	 * @param am
	 *            the am to set
	 */
	public void setAm(ApplicationManager am) {
		this.am = am;
	}

}
