package com.ctrip.platform.dal.dao.task;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ctrip.platform.dal.dao.DalHints;
import com.ctrip.platform.dal.dao.KeyHolder;
import com.ctrip.platform.dal.dao.StatementParameters;

public class CombinedInsertTask<T> extends InsertTaskAdapter<T> implements BulkTask<Integer, T> {
	public static final String TMPL_SQL_MULTIPLE_INSERT = "INSERT INTO %s(%s) VALUES %s";

	@Override
	public Integer getEmptyValue() {
		return 0;
	}	

	@Override
	public Integer execute(DalHints hints, Map<Integer, Map<String, ?>> daoPojos, List<T> rawPojos) throws SQLException {
		StatementParameters parameters = new StatementParameters();
		StringBuilder values = new StringBuilder();

		Set<String> unqualifiedColumns = filterUnqualifiedColumns(hints, daoPojos, rawPojos);
		
		List<String> finalInsertableColumns = buildValidColumnsForInsert(unqualifiedColumns);
		
		String insertColumns = combineColumns(finalInsertableColumns, COLUMN_SEPARATOR);
		
		int startIndex = 1;
		for (Integer index :daoPojos.keySet()) {
			Map<String, ?> pojo = daoPojos.get(index);
			
			removeUnqualifiedColumns(pojo, unqualifiedColumns);
			
			int paramCount = addParameters(startIndex, parameters, pojo, finalInsertableColumns);
			startIndex += paramCount;
			values.append(String.format("(%s),", combine("?", paramCount, ",")));
		}

		String sql = String.format(TMPL_SQL_MULTIPLE_INSERT,
				getTableName(hints), insertColumns,
				values.substring(0, values.length() - 2) + ")");

		KeyHolder keyHolder = hints.getKeyHolder();
		KeyHolder tmpHolder = keyHolder != null && keyHolder.isRequireMerge() ? new KeyHolder() : keyHolder;
		
		int count = client.update(sql, parameters, hints.setKeyHolder(tmpHolder));
		
		if(tmpHolder != null)
			keyHolder.addPatial(daoPojos.keySet().toArray(new Integer[daoPojos.size()]), tmpHolder);
		
		hints.setKeyHolder(keyHolder);
		return count;
	}

	@Override
	public BulkTaskResultMerger<Integer> createMerger() {
		return new ShardedIntResultMerger();
	}
}
