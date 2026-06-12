package ar.edu.unrn.cinesync.simulacion;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Genera el .xlsx con los resultados de la simulación comparativa (Apache POI).
 *
 * Hojas:
 *   1. Resumen — promedios de las repeticiones por (técnica, escenario, workers)
 *   2. Detalle — todas las corridas individuales
 *   3. Speedup — S = T1/Tp y eficiencia E = S/p por técnica, variando workers
 *      (solo si la simulación incluye la línea base de 1 worker)
 */
@Service
public class ExportacionExcelService {

    public byte[] generar(SimulacionState state) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Estilos estilos = new Estilos(wb);
            List<MetricasSimulacion> corridas = state.getResultados();

            crearHojaResumen(wb, estilos, corridas);
            crearHojaDetalle(wb, estilos, corridas);
            crearHojaSpeedup(wb, estilos, corridas);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando el Excel de resultados", e);
        }
    }

    // ── Hoja 1: Resumen ───────────────────────────────────────────────────

    private void crearHojaResumen(Workbook wb, Estilos estilos, List<MetricasSimulacion> corridas) {
        Sheet hoja = wb.createSheet("Resumen");
        String[] columnas = { "Técnica", "Escenario", "Workers", "Wall Clock (ms)",
                "Throughput (req/s)", "Reservas Exitosas", "Conflictos", "Latencia Prom (ms)" };
        crearCabecera(hoja, estilos, columnas);

        int fila = 1;
        for (List<MetricasSimulacion> grupo : agruparPorBloque(corridas).values()) {
            MetricasSimulacion ref = grupo.get(0);
            Row row = hoja.createRow(fila);
            boolean alterna = fila % 2 == 0;

            celdaTexto(row, 0, ref.tecnica(), estilos.texto(alterna));
            celdaTexto(row, 1, ref.escenario(), estilos.texto(alterna));
            celdaNumero(row, 2, ref.workers(), estilos.entero(alterna));
            celdaNumero(row, 3, promedio(grupo, MetricasSimulacion::wallClockTimeMs), estilos.decimal(alterna));
            celdaNumero(row, 4, promedio(grupo, MetricasSimulacion::throughput), estilos.decimal(alterna));
            celdaNumero(row, 5, promedio(grupo, MetricasSimulacion::reservasExitosas), estilos.decimal(alterna));
            celdaNumero(row, 6, promedio(grupo, MetricasSimulacion::conflictosDetectados), estilos.decimal(alterna));
            celdaNumero(row, 7, promedio(grupo, MetricasSimulacion::latenciaPromedioMs), estilos.decimal(alterna));
            fila++;
        }
        autoajustar(hoja, columnas.length);
    }

    // ── Hoja 2: Detalle ───────────────────────────────────────────────────

    private void crearHojaDetalle(Workbook wb, Estilos estilos, List<MetricasSimulacion> corridas) {
        Sheet hoja = wb.createSheet("Detalle");
        String[] columnas = { "Técnica", "Escenario", "Workers", "Repetición",
                "Wall Clock (ms)", "Throughput (req/s)", "Reservas Exitosas",
                "Reservas Fallidas", "Conflictos", "Latencia Prom (ms)",
                "Latencia Máx (ms)", "Latencia Mín (ms)" };
        crearCabecera(hoja, estilos, columnas);

        int fila = 1;
        for (MetricasSimulacion m : corridas) {
            Row row = hoja.createRow(fila);
            boolean alterna = fila % 2 == 0;

            celdaTexto(row, 0, m.tecnica(), estilos.texto(alterna));
            celdaTexto(row, 1, m.escenario(), estilos.texto(alterna));
            celdaNumero(row, 2, m.workers(), estilos.entero(alterna));
            celdaNumero(row, 3, m.repeticion(), estilos.entero(alterna));
            celdaNumero(row, 4, m.wallClockTimeMs(), estilos.entero(alterna));
            celdaNumero(row, 5, m.throughput(), estilos.decimal(alterna));
            celdaNumero(row, 6, m.reservasExitosas(), estilos.entero(alterna));
            celdaNumero(row, 7, m.reservasFallidas(), estilos.entero(alterna));
            celdaNumero(row, 8, m.conflictosDetectados(), estilos.entero(alterna));
            celdaNumero(row, 9, m.latenciaPromedioMs(), estilos.decimal(alterna));
            celdaNumero(row, 10, m.latenciaMaxMs(), estilos.entero(alterna));
            celdaNumero(row, 11, m.latenciaMinMs(), estilos.entero(alterna));
            fila++;
        }
        autoajustar(hoja, columnas.length);
    }

    // ── Hoja 3: Speedup ───────────────────────────────────────────────────

    private void crearHojaSpeedup(Workbook wb, Estilos estilos, List<MetricasSimulacion> corridas) {
        Sheet hoja = wb.createSheet("Speedup");
        String[] columnas = { "Técnica", "Escenario", "Workers (p)", "Tp (ms)",
                "T1 (ms)", "Speedup S=T1/Tp", "Eficiencia E=S/p" };
        crearCabecera(hoja, estilos, columnas);

        Map<String, List<MetricasSimulacion>> bloques = agruparPorBloque(corridas);

        // T1 por (técnica, escenario): base con 1 worker
        Map<String, Double> bases = new LinkedHashMap<>();
        for (List<MetricasSimulacion> grupo : bloques.values()) {
            MetricasSimulacion ref = grupo.get(0);
            if (ref.workers() == 1) {
                bases.put(ref.tecnica() + "|" + ref.escenario(),
                        promedio(grupo, MetricasSimulacion::wallClockTimeMs));
            }
        }

        int fila = 1;
        for (List<MetricasSimulacion> grupo : bloques.values()) {
            MetricasSimulacion ref = grupo.get(0);
            Double t1 = bases.get(ref.tecnica() + "|" + ref.escenario());
            if (t1 == null) continue;  // sin línea base de 1 worker: no hay speedup

            double tp = promedio(grupo, MetricasSimulacion::wallClockTimeMs);
            double speedup = tp > 0 ? t1 / tp : 0;
            double eficiencia = speedup / ref.workers();

            Row row = hoja.createRow(fila);
            boolean alterna = fila % 2 == 0;
            celdaTexto(row, 0, ref.tecnica(), estilos.texto(alterna));
            celdaTexto(row, 1, ref.escenario(), estilos.texto(alterna));
            celdaNumero(row, 2, ref.workers(), estilos.entero(alterna));
            celdaNumero(row, 3, tp, estilos.decimal(alterna));
            celdaNumero(row, 4, t1, estilos.decimal(alterna));
            celdaNumero(row, 5, speedup, estilos.decimal(alterna));
            celdaNumero(row, 6, eficiencia, estilos.decimal(alterna));
            fila++;
        }

        if (fila == 1) {
            Row row = hoja.createRow(1);
            celdaTexto(row, 0,
                    "Sin datos de speedup: la simulación no incluyó la línea base de 1 worker. "
                    + "Ejecutá la simulación sin fijar workers (barrido 1, 2, 4, 8).",
                    estilos.texto(false));
        }
        autoajustar(hoja, columnas.length);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Agrupa corridas por bloque (técnica, escenario, workers) preservando el orden. */
    private Map<String, List<MetricasSimulacion>> agruparPorBloque(List<MetricasSimulacion> corridas) {
        Map<String, List<MetricasSimulacion>> grupos = new LinkedHashMap<>();
        corridas.stream()
                .sorted(Comparator.comparing(MetricasSimulacion::escenario)
                        .thenComparingInt(MetricasSimulacion::workers)
                        .thenComparing(MetricasSimulacion::tecnica))
                .forEach(m -> grupos
                        .computeIfAbsent(m.tecnica() + "|" + m.escenario() + "|" + m.workers(),
                                k -> new java.util.ArrayList<>())
                        .add(m));
        return grupos;
    }

    private double promedio(List<MetricasSimulacion> grupo,
                            java.util.function.ToDoubleFunction<MetricasSimulacion> campo) {
        return grupo.stream().mapToDouble(campo).average().orElse(0);
    }

    private void crearCabecera(Sheet hoja, Estilos estilos, String[] columnas) {
        Row cabecera = hoja.createRow(0);
        for (int i = 0; i < columnas.length; i++) {
            Cell celda = cabecera.createCell(i);
            celda.setCellValue(columnas[i]);
            celda.setCellStyle(estilos.cabecera);
        }
    }

    private void celdaTexto(Row row, int col, String valor, CellStyle estilo) {
        Cell celda = row.createCell(col);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private void celdaNumero(Row row, int col, double valor, CellStyle estilo) {
        Cell celda = row.createCell(col);
        celda.setCellValue(valor);
        celda.setCellStyle(estilo);
    }

    private void autoajustar(Sheet hoja, int columnas) {
        for (int i = 0; i < columnas; i++) {
            hoja.autoSizeColumn(i);
        }
    }

    /**
     * Estilos del workbook, creados una sola vez (POI limita la cantidad de
     * CellStyle por archivo, no se deben crear por celda).
     */
    private static class Estilos {
        final CellStyle cabecera;
        private final CellStyle textoNormal, textoAlterno;
        private final CellStyle enteroNormal, enteroAlterno;
        private final CellStyle decimalNormal, decimalAlterno;

        Estilos(Workbook wb) {
            Font negrita = wb.createFont();
            negrita.setBold(true);
            negrita.setColor(IndexedColors.WHITE.getIndex());

            cabecera = wb.createCellStyle();
            cabecera.setFont(negrita);
            cabecera.setFillForegroundColor(IndexedColors.GREY_80_PERCENT.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            DataFormat formato = wb.createDataFormat();
            short dosDecimales = formato.getFormat("0.00");
            short entero = formato.getFormat("0");

            textoNormal    = wb.createCellStyle();
            enteroNormal   = wb.createCellStyle();
            decimalNormal  = wb.createCellStyle();
            enteroNormal.setDataFormat(entero);
            decimalNormal.setDataFormat(dosDecimales);

            textoAlterno   = alterno(wb, null);
            enteroAlterno  = alterno(wb, entero);
            decimalAlterno = alterno(wb, dosDecimales);
        }

        private static CellStyle alterno(Workbook wb, Short dataFormat) {
            CellStyle estilo = wb.createCellStyle();
            estilo.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            if (dataFormat != null) estilo.setDataFormat(dataFormat);
            return estilo;
        }

        CellStyle texto(boolean alterna)   { return alterna ? textoAlterno : textoNormal; }
        CellStyle entero(boolean alterna)  { return alterna ? enteroAlterno : enteroNormal; }
        CellStyle decimal(boolean alterna) { return alterna ? decimalAlterno : decimalNormal; }
    }
}
