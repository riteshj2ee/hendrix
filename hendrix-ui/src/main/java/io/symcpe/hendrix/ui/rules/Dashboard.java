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

import java.io.Serializable;

import javax.annotation.PostConstruct;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.SessionScoped;

import org.primefaces.model.chart.Axis;
import org.primefaces.model.chart.AxisType;
import org.primefaces.model.chart.LineChartModel;
import org.primefaces.model.chart.LineChartSeries;
import org.primefaces.model.chart.PieChartModel;

/**
 * JSF dashboard
 * 
 * @author ambud_sharma
 */
@ManagedBean(name="dash")
@SessionScoped
public class Dashboard implements Serializable {

	private static final long serialVersionUID = 1L;
	private LineChartModel alertStats;
	private PieChartModel topRules;
	
	public Dashboard() {
	}
	
	@PostConstruct
	public void init() {
		alertStats = new LineChartModel();
		initLinearModel();

        topRules = new PieChartModel();
        initializeTopRules();
	}
	
	private void initializeTopRules() {
		topRules.clear();
		topRules.set("Bad Hosts", 540);
		topRules.set("Test Aggregations", 325);
		topRules.set("DB Error 504", 10);
		topRules.setLegendPosition("w");
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
	
}
