package it.govpay.maggioli.batch.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CSVUtilsTest {

    @Test
    @DisplayName("getInstance() should return a non-null CSVUtils with default format")
    void testGetInstanceDefault() {
        CSVUtils utils = CSVUtils.getInstance();

        assertThat(utils).isNotNull();
    }

    @Test
    @DisplayName("getInstance(null) should return CSVUtils with RFC4180 format")
    void testGetInstanceWithNull() {
        CSVUtils utils = CSVUtils.getInstance(null);

        assertThat(utils).isNotNull();
        assertThat(utils.getDelimiter()).isEqualTo(",");
    }

    @Test
    @DisplayName("getInstance(custom) should use the provided format")
    void testGetInstanceWithCustomFormat() {
        CSVFormat customFormat = CSVFormat.DEFAULT.builder().setDelimiter(';').build();
        CSVUtils utils = CSVUtils.getInstance(customFormat);

        assertThat(utils.getDelimiter()).isEqualTo(";");
    }

    @Test
    @DisplayName("isEmpty should return true for empty field")
    void testIsEmptyTrue() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();
        CSVRecord record = utils.getCSVRecord("a,,c");

        assertThat(utils.isEmpty(record, 1)).isTrue();
    }

    @Test
    @DisplayName("isEmpty should return false for non-empty field")
    void testIsEmptyFalse() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();
        CSVRecord record = utils.getCSVRecord("a,b,c");

        assertThat(utils.isEmpty(record, 1)).isFalse();
    }

    @Test
    @DisplayName("isEmpty should return true for out-of-bounds position")
    void testIsEmptyOutOfBounds() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();
        CSVRecord record = utils.getCSVRecord("a,b");

        assertThat(utils.isEmpty(record, 99)).isTrue();
    }

    @Test
    @DisplayName("toJsonValue with single non-empty field should return quoted value")
    void testToJsonValueSingleField() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();
        CSVRecord record = utils.getCSVRecord("hello,world");

        String result = utils.toJsonValue(record, 0);

        assertThat(result).isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("toJsonValue with multiple fields should return concatenated quoted value")
    void testToJsonValueMultipleFields() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();
        CSVRecord record = utils.getCSVRecord("hello,world");

        String result = utils.toJsonValue(record, 0, 1);

        assertThat(result).isEqualTo("\"hello world\"");
    }

    @Test
    @DisplayName("toJsonValue with all empty fields should return null")
    void testToJsonValueAllEmpty() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();
        CSVRecord record = utils.getCSVRecord(",");

        String result = utils.toJsonValue(record, 0, 1);

        assertThat(result).isEqualTo("null");
    }

    @Test
    @DisplayName("toCsv should produce a correct CSV line")
    void testToCsv() throws IOException {
        CSVUtils utils = CSVUtils.getInstance();

        String csv = utils.toCsv("a", "b", "c");

        assertThat(csv.trim()).isEqualTo("a,b,c");
    }

    @Test
    @DisplayName("getDelimiter should return the delimiter of the format")
    void testGetDelimiter() {
        CSVUtils utils = CSVUtils.getInstance();

        assertThat(utils.getDelimiter()).isEqualTo(",");
    }

    @Test
    @DisplayName("splitCSV should skip first rows and return the rest")
    void testSplitCSVWithSkip() throws IOException {
        String csv = "header1,header2\nrow1col1,row1col2\nrow2col1,row2col2";
        byte[] data = csv.getBytes();

        List<byte[]> result = CSVUtils.splitCSV(data, 1);

        assertThat(result).hasSize(2);
        assertThat(new String(result.get(0))).isEqualTo("row1col1,row1col2");
        assertThat(new String(result.get(1))).isEqualTo("row2col1,row2col2");
    }
}
