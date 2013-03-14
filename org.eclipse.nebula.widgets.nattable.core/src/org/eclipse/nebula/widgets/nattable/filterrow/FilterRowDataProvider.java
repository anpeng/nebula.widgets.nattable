/*******************************************************************************
 * Copyright (c) 2012 Original authors and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Original authors and others - initial API and implementation
 ******************************************************************************/
package org.eclipse.nebula.widgets.nattable.filterrow;

import static org.eclipse.nebula.widgets.nattable.util.ObjectUtils.isEmpty;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.nebula.widgets.nattable.config.CellConfigAttributes;
import org.eclipse.nebula.widgets.nattable.config.IConfigRegistry;
import org.eclipse.nebula.widgets.nattable.data.IDataProvider;
import org.eclipse.nebula.widgets.nattable.data.convert.IDisplayConverter;
import org.eclipse.nebula.widgets.nattable.filterrow.event.FilterAppliedEvent;
import org.eclipse.nebula.widgets.nattable.layer.ILayer;
import org.eclipse.nebula.widgets.nattable.persistence.IPersistable;
import org.eclipse.nebula.widgets.nattable.style.DisplayMode;
import org.eclipse.nebula.widgets.nattable.util.ObjectUtils;
import org.eclipse.nebula.widgets.nattable.util.PersistenceUtils;


/**
 * Data provider for the filter row<br/>
 * - Stores filter strings
 * - Applies them to the {@link MatcherEditor} on the {@link FilterList}
 */
public class FilterRowDataProvider<T> implements IDataProvider, IPersistable {

	private static final Log log = LogFactory.getLog(FilterRowDataProvider.class);

	/**
	 * The {@link IFilterStrategy} to which the set filter value should be applied.
	 */
	private final IFilterStrategy<T> filterStrategy;
	/**
	 * The column header layer where this {@link IDataProvider} is used for filtering.
	 * Needed for retrieval of column indexes and firing according filter events.
	 */
	private final ILayer columnHeaderLayer;
	/**
	 * The {@link IDataProvider} of the column header.
	 * This is necessary to retrieve the real column count of the column header and not a
	 * transformed one. (e.g. hiding a column would change the column count in the column header
	 * but not in the column header {@link IDataProvider}).
	 */
	private final IDataProvider columnHeaderDataProvider;
	
	/**
	 * The {@link IConfigRegistry} needed to retrieve the {@link IDisplayConverter} for converting
	 * the values on state save/load operations.
	 */
	private final IConfigRegistry configRegistry;
	
	/**
	 * Contains the filter objects mapped to the column index.
	 */
	private Map<Integer, Object> filterIndexToObjectMap = new HashMap<Integer, Object>();
	
	/**
	 * 
	 * @param filterStrategy The {@link IFilterStrategy} to which the set filter value should be applied.
	 * @param columnHeaderLayer The column header layer where this {@link IDataProvider} is used for filtering 
	 * 			needed for retrieval of column indexes and firing according filter events..
	 * @param columnHeaderDataProvider The {@link IDataProvider} of the column header needed to retrieve the real 
	 * 			column count of the column header and not a transformed one.
	 * @param configRegistry The {@link IConfigRegistry} needed to retrieve the {@link IDisplayConverter} for 
	 * 			converting the values on state save/load operations.
	 */
	public FilterRowDataProvider(IFilterStrategy<T> filterStrategy, ILayer columnHeaderLayer, 
			IDataProvider columnHeaderDataProvider, IConfigRegistry configRegistry) {
		this.filterStrategy = filterStrategy;
		this.columnHeaderLayer = columnHeaderLayer;
		this.columnHeaderDataProvider = columnHeaderDataProvider;
		this.configRegistry = configRegistry;
	}
	
	/**
	 * 
	 * @param filterIndexToObjectMap
	 */
	public void setFilterIndexToObjectMap(Map<Integer, Object> filterIndexToObjectMap) {
		this.filterIndexToObjectMap = filterIndexToObjectMap;
	}

	@Override
	public int getColumnCount() {
		return columnHeaderDataProvider.getColumnCount();
	}

	@Override
	public Object getDataValue(int columnIndex, int rowIndex) {
		return filterIndexToObjectMap.get(columnIndex);
	}

	@Override
	public int getRowCount() {
		return 1;
	}

	@Override
	public void setDataValue(int columnIndex, int rowIndex, Object newValue) {
		columnIndex = columnHeaderLayer.getColumnIndexByPosition(columnIndex);

		if (ObjectUtils.isNotNull(newValue)) {
			filterIndexToObjectMap.put(columnIndex, newValue);
		} else {
			filterIndexToObjectMap.remove(columnIndex);
		}

		filterStrategy.applyFilter(filterIndexToObjectMap);

		columnHeaderLayer.fireLayerEvent(new FilterAppliedEvent(columnHeaderLayer));
	}

	// Load/save state

	@Override
	public void saveState(String prefix, Properties properties) {
		Map<Integer, String> filterTextByIndex = new HashMap<Integer, String>();
		for(Integer columnIndex : filterIndexToObjectMap.keySet()) {
			final IDisplayConverter converter = configRegistry.getConfigAttribute(
					CellConfigAttributes.DISPLAY_CONVERTER, 
					DisplayMode.NORMAL, 
					FilterRowDataLayer.FILTER_ROW_COLUMN_LABEL_PREFIX + columnIndex);
			filterTextByIndex.put(columnIndex, (String) converter.canonicalToDisplayValue(filterIndexToObjectMap.get(columnIndex)));
		}
		
		String string = PersistenceUtils.mapAsString(filterTextByIndex);

		if (!isEmpty(string)) {
			properties.put(prefix + FilterRowDataLayer.PERSISTENCE_KEY_FILTER_ROW_TOKENS, string);
		}
	}
	
	@Override
	public void loadState(String prefix, Properties properties) {
		filterIndexToObjectMap.clear();
		
		try {
			Object property = properties.get(prefix + FilterRowDataLayer.PERSISTENCE_KEY_FILTER_ROW_TOKENS);
			Map<Integer, String> filterTextByIndex = PersistenceUtils.parseString(property);
			for (Integer columnIndex : filterTextByIndex.keySet()) {
				final IDisplayConverter converter = configRegistry.getConfigAttribute(
						CellConfigAttributes.DISPLAY_CONVERTER, 
						DisplayMode.NORMAL, 
						FilterRowDataLayer.FILTER_ROW_COLUMN_LABEL_PREFIX + columnIndex);
				filterIndexToObjectMap.put(columnIndex, converter.displayToCanonicalValue(filterTextByIndex.get(columnIndex)));
			}
		} catch (Exception e) {
			log.error("Error while restoring filter row text!", e); //$NON-NLS-1$
		}
		
		filterStrategy.applyFilter(filterIndexToObjectMap);
	}

	/**
	 * Clear all filters that are currently applied.
	 */
	public void clearAllFilters() {
		filterIndexToObjectMap.clear();
		filterStrategy.applyFilter(filterIndexToObjectMap);
	}
	
}
