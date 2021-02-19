// package org.mitre.synthea.export;

// import java.util.HashMap;

// /**
//  * Exporter Data Builder
//  */
// public class ExportDataBuilder {
//   private ExportConfig exportConfig = null;

//   /** constructor
//    *  @param configFilePath the path to the configuration file
//    */
//   public ExportDataBuilder( String configFilePath ) {
//     this.exportConfig = new ExportConfig( "src/main/resources/exporters/cms_field_values.tsv" );
//   }

//   public HashMap setKnown( String which, HashMap fieldValues ) {
//     fieldValues.clear();





// // todo:  code PR by tomorrow morning
// //     most productive work first; you ain't gonna need it





//     try {
//       List<ExportConfigEntry> configs = exportConfig.getAllConfigs();
//       System.out.println("^^^^^"+configs.get(0));

//       int propCount = 0;
//       int fixedValuePropsProcessed = 0;
//       for ( ExportConfigEntry prop: configs ) {
//         String cell = null;//
//         switch ( which ) {
//           case "inpatient": cell = prop.getInpatient(); break;
//         }
//         if ( !cell.isEmpty() ) {
//           propCount++;
//           fixedValuePropsProcessed ++;
//           String value = cell;
//           String comment = null;
//           int commentStart = cell.indexOf("(");
//           if ( commentStart >= 0 ) {
//             value = cell.substring(0, commentStart - 1);
//             comment = cell.substring(commentStart + 1, cell.length()-1);
//           }
//           System.out.println("value: " + value);
//           System.out.println("comment: " + comment);
//           InpatientFields fieldEnum = InpatientFields.valueOf(prop.getField());
//           fieldValues.put(fieldEnum, value);
//           // System.out.printf("config: %s\n", prop.toMinString( false ) );
//           // System.out.printf("%61s: %s\n", "--> processed fieldValue", fieldValues.get(fieldEnum));
//         }
//       }

//       System.out.println("props defined:" + propCount );
//       System.out.println("fixed value props processed:" + fixedValuePropsProcessed );
//     }
//     catch (Exception ex) {
//       System.out.println("exportInpatient ERROR:  " + ex);
//     }

//     return fieldValues;
//   }
// }