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
package io.symcpe.hendrix.api.dao;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Queries database to find the shortest id available to be used as the id field else will give the highest available value.
 * 
 * This Generator resolves the issue documented in issue DS-6001. Here's the summary:
 * 
 * There's an upper limit on total number of rules and templates in Hendrix; this limit is 32K per instance of Hendrix.
 * The reason behind this is performance and efficiency because ruleids are using in aggregation (Type 3-Type 5) rules having integer ruleid has implications from performance perspective in storing, hashing as well as CPU cache alignment.
 * The byproduct of this will also make sure Hendrix has good performance, because running more than 32K rules (depending on rule complexity) will substantially slow down the pipeline make the engine less real-time.
 * 
 * The issue is that Hibernate sequence generator is used in API to auto generate these ruleids however the issue is these IDs in hibernate are monotonously increasing in nature which is bound to cause issues because after 32K transactions the rule ids in the range will run out and no more new rules can be created without manually resetting the sequence table in hibernate.
 * To fix this issue a custom generator must be designed for generating these sequence ids. This will remove the number of transactions while keeping the limit on rule ids / template ids.
 * <br><br>
 * NOTE: This generator uses MySQL compliant SQL syntax. 
 * @author ambud_sharma
 */
public abstract class AbstractIdGenerator implements IdentifierGenerator {

	@Override
	public Serializable generate(SessionImplementor session, Object object) throws HibernateException {
		Connection connection = session.connection();
		try {
			Statement statement = connection.createStatement();
			try {
				boolean consecutive = areIdsConsecutive(statement);
				short id = findSmallestAvailableId(statement, consecutive);
				return id;
			} catch (SQLException e) {
				throw new HibernateException(e);
			} finally {
				statement.close();
			}
		} catch (Exception e) {
			throw new HibernateException(e);
		}
	}

	public boolean areIdsConsecutive(Statement statement) throws SQLException {
		ResultSet rs1 = statement.executeQuery("select count(*) from " + getTableName());
		int count = 0;
		if (rs1.next()) {
			count = rs1.getInt(1);
		}
		if (count == 0) {
			return true;
		} else {
			ResultSet rs2 = statement.executeQuery("select sum(" + getIdColumn() + ") as Id from " + getTableName());
			if (rs2.next()) {
				int sum = rs2.getInt(1);
				int nNumberSum = (count * (count + 1) / 2);
				if (sum != nNumberSum) {
					return false;
				} else {
					return true;
				}
			}
			return true;
		}
	}

	public short findSmallestAvailableId(Statement statement, boolean consecutives) throws SQLException {
		short id = 0;
		if (consecutives) {
			// if all ids in the database don't have any consolidation room then
			// get the largest id and return that
			ResultSet rs = statement.executeQuery("select " + getIdColumn() + " as Id from " + getTableName()
					+ " order by " + getIdColumn() + " desc limit 1");
			if (rs.next()) {
				id = (short) (rs.getShort(1) + 1);
			} else {
				return 1;
			}
		} else {
			ResultSet rs = statement.executeQuery("select a." + getIdColumn() + "+1 missing_id from " + getTableName()
					+ " a\n" + "where a." + getIdColumn() + "+1 not in (select " + getIdColumn() + " from "
					+ getTableName() + " b where b." + getIdColumn() + "=a." + getIdColumn() + "+1)\n" + "and a."
					+ getIdColumn() + "!=(select " + getIdColumn() + " from " + getTableName() + " c order by "
					+ getIdColumn() + " desc limit 1)");
			if (rs.next()) {
				return rs.getShort(1);
			} else {
				return 1;
			}
		}
		return id;
	}

	public abstract String getIdColumn();

	public abstract String getTableName();

}
