package cn.cvte.scm.integration.adapter;

import cn.cvte.scm.common.BusinessException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class FileAdapter {
  public List<InputRow> read(MultipartFile file) {
    String name = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase();
    if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
      throw BusinessException.bad("文件为空或超过5MB");
    try {
      if (name.endsWith(".csv")) return csv(file);
      if (name.endsWith(".xlsx") || name.endsWith(".xls")) return excel(file);
      throw BusinessException.bad("仅支持 UTF-8 CSV、XLS、XLSX");
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw BusinessException.bad("文件格式损坏、CSV引号未闭合或表头重复，请检查文件");
    }
  }

  private List<InputRow> csv(MultipartFile f) throws Exception {
    String text =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(f.getBytes()))
            .toString();
    if (text.startsWith("\uFEFF")) text = text.substring(1);
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(false)
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .get()
            .parse(new StringReader(text))) {
      List<InputRow> rows = new ArrayList<>();
      for (var r : parser) {
        if (rows.size() >= 10000) throw BusinessException.bad("单次导入最多10000行");
        rows.add(
            new InputRow(
                (int) r.getRecordNumber() + 1,
                r.isConsistent() ? r.toMap() : Map.of(),
                r.isConsistent() ? null : "列数与表头不一致"));
      }
      return rows;
    }
  }

  private List<InputRow> excel(MultipartFile f) throws Exception {
    try (var book = WorkbookFactory.create(f.getInputStream())) {
      var sheet = book.getSheetAt(0);
      if (sheet.getLastRowNum() > 10000) throw BusinessException.bad("单次导入最多10000行");
      var head = sheet.getRow(0);
      if (head == null) throw BusinessException.bad("缺少表头");
      DataFormatter fmt = new DataFormatter(Locale.ROOT);
      List<String> headers = new ArrayList<>();
      for (int col = 0; col < head.getLastCellNum(); col++) {
        Cell c = head.getCell(col);
        String h = fmt.formatCellValue(c).trim();
        if (h.isEmpty() || headers.contains(h)) throw BusinessException.bad("表头为空或重复");
        headers.add(h);
      }
      List<InputRow> result = new ArrayList<>();
      for (int i = 1; i <= sheet.getLastRowNum(); i++) {
        var row = sheet.getRow(i);
        Map<String, String> values = new LinkedHashMap<>();
        String error = row != null && row.getLastCellNum() > headers.size() ? "数据列数超过表头" : null;
        for (int k = 0; k < headers.size(); k++) {
          Cell c = row == null ? null : row.getCell(k);
          if (c != null && c.getCellType() == CellType.FORMULA) error = "不接受公式单元格，请粘贴为值";
          String v =
              c == null
                  ? ""
                  : c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)
                      ? c.getLocalDateTimeCellValue().toString()
                      : fmt.formatCellValue(c);
          values.put(headers.get(k), v);
        }
        result.add(new InputRow(i + 1, values, error));
      }
      return result;
    }
  }
}
