package org.molgenis.ui.genenetwork.matrix.impl;

import org.molgenis.data.DataService;
import org.molgenis.data.Entity;
import org.molgenis.data.MolgenisDataException;
import org.molgenis.file.FileStore;
import org.molgenis.file.model.FileMeta;
import org.molgenis.ui.genenetwork.matrix.meta.MatrixMetadata;
import org.molgenis.ui.genenetwork.matrix.model.Score;
import org.molgenis.ui.genenetwork.matrix.service.MatrixService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static org.molgenis.ui.genenetwork.matrix.factory.DoubleMatrixFactory.createDoubleMatrix;
import static org.molgenis.ui.genenetwork.matrix.factory.MatrixMapperFactory.createMatrixMapper;
import static org.molgenis.ui.genenetwork.matrix.meta.MatrixMetadata.*;
import static org.springframework.web.bind.annotation.RequestMethod.GET;

@Service
@RequestMapping("/api/matrix")
public class MatrixServiceImpl implements MatrixService
{
	private DataService dataService;
	private FileStore fileStore;

	@Autowired
	public MatrixServiceImpl(DataService dataService, FileStore fileStore)
	{
		this.dataService = requireNonNull(dataService);
		this.fileStore = requireNonNull(fileStore);
	}

	@Override
	@RequestMapping(value = "/{entityId}/valueByIndex", method = GET)
	@ResponseBody
	public Object getValueByIndex(@PathVariable("entityId") String entityName, @RequestParam("row") int row,
			@RequestParam("column") int column) throws MolgenisDataException
	{
		DoubleMatrix matrix = getMatrixByEntityTypeId(entityName);
		return matrix.getValueByIndex(row, column);
	}

	@RequestMapping(value = "/{entityId}/valueByNames", method = GET)
	@ResponseBody
	public List<Score> getValueByNames(@PathVariable("entityId") String entityName, @RequestParam("rows") String rows,
			@RequestParam("columns") String columns) throws MolgenisDataException
	{
		List<Score> results = new ArrayList<>();
		DoubleMatrix matrix = getMatrixByEntityTypeId(entityName);

		for (String row : rows.split(","))
		{
			MatrixMapperImpl rowMapper = matrix.getRowMapper();
			String translatedRow;
			if (rowMapper != null) translatedRow = rowMapper.map(row);
			else translatedRow = row;
			for (String column : columns.split(","))
			{
				MatrixMapperImpl columnMapper = matrix.getColumnMapper();
				if (columnMapper != null) column = columnMapper.map(column);
				results.add(Score.createScore(column, row, matrix.getValueByName(translatedRow, column)));
			}
		}
		return results;
	}

	private DoubleMatrix getMatrixByEntityTypeId(String entityId)
	{
		Entity entity = dataService.findOneById(MATRIX_METADATA, entityId);
		if (entity != null)
		{
			return getMatrix(entity);
		}
		throw new MolgenisDataException("Unknown Matrix Metadata EntityID [" + entityId + "]");
	}

	private DoubleMatrix getMatrix(Entity entity)
	{
		String fileLocation = entity.getString(MatrixMetadata.FILE_LOCATION);
		char separator = getSeparatorValue(entity.getString(MatrixMetadata.SEPARATOR));

		DoubleMatrix doubleMatrix = createDoubleMatrix(new File(fileLocation), separator);

		if (entity.getEntity(COLUMN_MAPPING_FILE) != null)
			doubleMatrix.setColumnMapper(getMapper(entity, COLUMN_MAPPING_FILE));
		if (entity.getEntity(ROW_MAPPING_FILE) != null) doubleMatrix.setRowMapper(getMapper(entity, ROW_MAPPING_FILE));

		return doubleMatrix;
	}

	private MatrixMapperImpl getMapper(Entity entity, String mapping)
	{
		FileMeta meta = entity.getEntity(mapping, FileMeta.class);
		File file = fileStore.getFile(meta.getId());
		return createMatrixMapper(file);
	}
}
