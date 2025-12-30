/*
 * GovPay - Porta di Accesso al Nodo dei Pagamenti SPC
 * http://www.gov4j.it/govpay
 *
 * Copyright (c) 2014-2025 Link.it srl (http://www.link.it).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3, as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package it.govpay.maggioli.batch.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CSVUtils {
	private CSVFormat csvFormat = CSVFormat.RFC4180;

	public CSVUtils(CSVFormat csvFormat) {
		this.csvFormat = csvFormat;
	}

	public static CSVUtils getInstance() {
		return new CSVUtils(CSVFormat.DEFAULT);
	}

	public static CSVUtils getInstance(CSVFormat csvFormat) {
		if(csvFormat != null)
			return new CSVUtils(csvFormat);
		else
			return new CSVUtils(CSVFormat.RFC4180);
	}

	public static List<byte[]> splitCSV(InputStream is, long skip) throws IOException {
		InputStreamReader isr = new InputStreamReader(is);
		BufferedReader br = new BufferedReader(isr);

		List<byte[]> lst = new ArrayList<>(); 

		while(br.ready()) {
			String readLine = br.readLine();
			if(skip > 0) {
				skip--;
			} else {
				if(readLine != null) {
					lst.add(readLine.getBytes());
				}
			}
		}
		return lst;
	}

	public static List<byte[]> splitCSV(byte[] tracciato, long skip) throws IOException {
		return splitCSV(new ByteArrayInputStream(tracciato), skip);
	}


	public CSVRecord getCSVRecord(String csvEntry) throws IOException {
		log.trace("Parsing del record CSV: [{}] [Delimiter: {} Escape:{}]", csvEntry, csvFormat.getDelimiterString(), csvFormat.getEscapeCharacter());
		CSVParser p = CSVParser.parse(csvEntry, csvFormat);
		CSVRecord r = p.getRecords().get(0);
		if(log.isTraceEnabled()) {
			log.trace("Parsing completed:" );
			for(int i=0; i < r.size(); i++) {
				log.trace("{}: [{}]", i , r.get(i));
			}
		}
		return r ;
	}

	public boolean isEmpty(CSVRecord myRecord, int position) {
		try {
			return myRecord.get(position).isEmpty(); 
		} catch (Throwable t) {
			return true;
		}
	}

	public String toJsonValue(CSVRecord myRecord, int ... positions) {
		StringBuilder collage = new StringBuilder("");

		for(int position : positions) {
			if(!isEmpty(myRecord, position)) {
				collage.append(myRecord.get(position)).append(" ");
			}
		}

		if(collage.toString().trim().isEmpty())
			return "null";
		else
			return "\"" + collage.toString().trim() +  "\"";
	}

	public String toCsv(String ...strings) throws IOException {
		StringWriter writer = new StringWriter();
		CSVPrinter printer = new CSVPrinter(writer, csvFormat);
		printer.printRecord(Arrays.asList(strings));
		printer.flush();
		printer.close();
		
		return writer.toString();
	}

	public String getDelimiter() {
		return this.csvFormat.getDelimiterString();
	}
}
