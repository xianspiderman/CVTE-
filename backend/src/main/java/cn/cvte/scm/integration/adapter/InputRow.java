package cn.cvte.scm.integration.adapter;

import java.util.Map;

public record InputRow(int line, Map<String, String> values, String error) {}
