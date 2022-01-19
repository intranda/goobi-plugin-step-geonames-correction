package de.intranda.goobi.plugins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@AllArgsConstructor
@ToString
public class NEREntry {
    private String ID;
    private String title;
    private String doc_vocab;
    private String text_snippet;
    private int position;
    private String geonames_uri;
    private String geonames_feature_code;
    private String geonames_vocab;
    private String lat;
    private String lng;
    private String verification;
    private int occurence;
}
