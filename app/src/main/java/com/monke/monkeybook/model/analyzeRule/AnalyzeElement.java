package com.monke.monkeybook.model.analyzeRule;

import android.text.TextUtils;

import com.monke.monkeybook.help.FormatWebText;
import com.monke.monkeybook.utils.NetworkUtil;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.text.TextUtils.isEmpty;

/**
 * Created by GKF on 2018/1/25.
 * 书源规则解析
 */

public class AnalyzeElement {
    private String baseURL;
    private Element element;

    public AnalyzeElement(Element element, String baseURL) {
        this.element = element;
        this.baseURL = baseURL;
    }

    /**
     * 获取Elements
     */
    public static Elements getElements(Element temp, String rule) {
        Elements elements = new Elements();
        if (temp == null || isEmpty(rule)) {
            return elements;
        }
        String elementsType;
        String[] ruleStrS;
        if (rule.contains("&")) {
            elementsType = "&";
            ruleStrS = rule.split("&");
        } else if (rule.contains("%")) {
            elementsType = "%";
            ruleStrS = rule.split("%");
        } else {
            elementsType = "|";
            ruleStrS = rule.split("\\|");
        }
        List<Elements> elementsList = new ArrayList<>();
        for (String ruleStr : ruleStrS) {
            Elements tempS = getElementsSingle(temp, ruleStr);
            elementsList.add(tempS);
            if (elements.size() > 0 && elementsType.equals("|")) {
                break;
            }
        }
        if (elementsList.size() > 0) {
            switch (elementsType) {
                case "%":
                    for (int i = 0; i < elementsList.get(0).size(); i++) {
                        for (Elements es : elementsList) {
                            if (i < es.size()) {
                                elements.add(es.get(i));
                            }
                        }
                    }
                    break;
                default:
                    for (Elements es : elementsList) {
                        elements.addAll(es);
                    }
            }
        }
        return elements;
    }

    private static Elements filterElements(Elements elements, String[] rules) {
        if (rules == null || rules.length < 2) return elements;
        Elements selectedEls = new Elements();
        for (Element ele : elements) {
            boolean isOk = false;
            switch (rules[0]) {
                case "class":
                    isOk = ele.getElementsByClass(rules[1]).size() > 0;
                    break;
                case "id":
                    isOk = ele.getElementById(rules[1]) != null;
                    break;
                case "tag":
                    isOk = ele.getElementsByTag(rules[1]).size() > 0;
                    break;
                case "text":
                    isOk = ele.getElementsContainingOwnText(rules[1]).size() > 0;
                    break;
            }
            if (isOk) {
                selectedEls.add(ele);
            }
        }
        return selectedEls;
    }

    /**
     * 获取Elements按照一个规则
     */
    private static Elements getElementsSingle(Element temp, String rule) {
        Elements elements = new Elements();
        try {
            String[] rs = rule.trim().split("@");
            if (rs.length > 1) {
                elements.add(temp);
                for (String rl : rs) {
                    Elements es = new Elements();
                    for (Element et : elements) {
                        es.addAll(getElements(et, rl));
                    }
                    elements.clear();
                    elements.addAll(es);
                }
            } else {
                String[] rulePcx = rule.split("!");
                String[] rulePc = rulePcx[0].trim().split(">");
                String[] rules = rulePc[0].trim().split("\\.");
                String[] filterRules = null;
                boolean needFilterElements = rulePc.length > 1 && !isEmpty(rulePc[1].trim());
                if (needFilterElements) {
                    filterRules = rulePc[1].trim().split("\\.");
                    filterRules[0] = filterRules[0].trim();
                    List<String> validKeys = Arrays.asList("class", "id", "tag", "text");
                    if (filterRules.length < 2 || !validKeys.contains(filterRules[0]) || isEmpty(filterRules[1].trim())) {
                        needFilterElements = false;
                    }
                    filterRules[1] = filterRules[1].trim();
                }
                switch (rules[0]) {
                    case "children":
                        Elements children = temp.children();
                        if (needFilterElements)
                            children = filterElements(children, filterRules);
                        elements.addAll(children);
                        break;
                    case "class":
                        Elements elementsByClass = temp.getElementsByClass(rules[1]);
                        if (rules.length == 3) {
                            int index = Integer.parseInt(rules[2]);
                            if (index < 0) {
                                elements.add(elementsByClass.get(elementsByClass.size() + index));
                            } else {
                                elements.add(elementsByClass.get(index));
                            }
                        } else {
                            if (needFilterElements)
                                elementsByClass = filterElements(elementsByClass, filterRules);
                            elements.addAll(elementsByClass);
                        }
                        break;
                    case "tag":
                        Elements elementsByTag = temp.getElementsByTag(rules[1]);
                        if (rules.length == 3) {
                            int index = Integer.parseInt(rules[2]);
                            if (index < 0) {
                                elements.add(elementsByTag.get(elementsByTag.size() + index));
                            } else {
                                elements.add(elementsByTag.get(index));
                            }
                        } else {
                            if (needFilterElements)
                                elementsByTag = filterElements(elementsByTag, filterRules);
                            elements.addAll(elementsByTag);
                        }
                        break;
                    case "id":
                        elements.add(temp.getElementById(rules[1]));
                        break;
                    case "text":
                        Elements elementsByText = temp.getElementsContainingOwnText(rules[1]);
                        if (needFilterElements)
                            elementsByText = filterElements(elementsByText, filterRules);
                        elements.addAll(elementsByText);
                        break;
                }
                if (rulePcx.length > 1) {
                    String[] rulePcs = rulePcx[1].split(":");
                    if (rulePcs.length < elements.size() - 1) {
                        for (String pc : rulePcs) {
                            int pcInt = Integer.parseInt(pc);
                            if (pcInt < 0 && elements.size() + pcInt >= 0) {
                                elements.set(elements.size() + pcInt, null);
                            } else if (Integer.parseInt(pc) < elements.size()) {
                                elements.set(Integer.parseInt(pc), null);
                            }
                        }
                        Elements es = new Elements();
                        es.add(null);
                        elements.removeAll(es);
                    }
                }
            }
        } catch (Exception ignore) {
        }
        return elements;
    }

    /**
     * 合并内容列表,得到内容
     */
    public String getResult(String ruleStr) {
        if (isEmpty(ruleStr)) {
            return null;
        }
        String regex = null;
        String replacement = "";
        String result = "";
        //分离正则表达式
        String[] ruleStrS = ruleStr.trim().split("#");
        if (ruleStrS.length > 1) {
            regex = ruleStrS[1];
        }
        if (ruleStrS.length > 2 && !isEmpty(ruleStrS[2])) {
            replacement = ruleStrS[2];
        }
        if (isEmpty(ruleStrS[0])) {
            result = element.data();
        } else {
            List<String> textS = getAllResultList(ruleStrS[0]);
            if (textS.size() == 0) {
                return null;
            }
            StringBuilder content = new StringBuilder();
            for (String text : textS) {
                text = FormatWebText.getContent(text);
                if (textS.size() > 1) {
                    if (text.length() > 0) {
                        if (content.length() > 0) {
                            content.append("\n");
                        }
                        content.append("\u3000\u3000").append(text);
                    }
                } else {
                    content.append(text);
                }
                result = content.toString();
            }
        }
        if (!isEmpty(regex)) {
            assert regex != null;
            result = result.replaceAll(regex, replacement);
        }
        return result.trim();
    }

    public String getResultUrl(String ruleStr) {
        List<String> urlList = getAllResultList(ruleStr);
        if (urlList.size() > 0) {
            return urlList.get(0);
        }
        return null;
    }

    /**
     * 获取所有内容列表
     */
    public List<String> getAllResultList(String ruleStr) {
        List<String> textS = new ArrayList<>();
        if (isEmpty(ruleStr)) {
            return textS;
        }
        String regex = null;
        String replacement = "";
        //分离正则表达式
        String[] ruleStrS = ruleStr.trim().split("#");
        if (ruleStrS.length > 1) {
            regex = ruleStrS[1];
        }
        if (ruleStrS.length > 2 && !isEmpty(ruleStrS[2])) {
            replacement = ruleStrS[2];
        }
        if (isEmpty(ruleStrS[0])) {
            textS.add(element.data());
        } else {
            boolean isAnd;
            if (ruleStrS[0].contains("&")) {
                isAnd = true;
                ruleStrS = ruleStrS[0].split("&");
            } else {
                isAnd = false;
                ruleStrS = ruleStrS[0].split("\\|");
            }
            for (String ruleStrX : ruleStrS) {
                List<String> temp = getResultList(ruleStrX);
                if (temp != null) {
                    textS.addAll(temp);
                }
                if (textS.size() > 0 && !isAnd) {
                    break;
                }
            }
        }
        if (!isEmpty(regex)) {
            List<String> tempList = new ArrayList<>(textS);
            textS.clear();
            for (String text : tempList) {
                assert regex != null;
                text = text.replaceAll(regex, replacement);
                if (text.length() > 0) {
                    textS.add(text);
                }
            }
        }
        return textS;
    }

    /**
     * 获取内容列表
     */
    private List<String> getResultList(String ruleStr) {
        if (isEmpty(ruleStr)) {
            return null;
        }
        Elements elements = new Elements();
        elements.add(element);
        String[] rules = ruleStr.split("@");
        for (int i = 0; i < rules.length - 1; i++) {
            Elements es = new Elements();
            for (Element elt : elements) {
                es.addAll(getElementsSingle(elt, rules[i]));
            }
            elements.clear();
            elements = es;
        }
        if (elements.isEmpty()) {
            return null;
        }
        return getResultLast(elements, rules[rules.length - 1]);
    }

    /**
     * 根据最后一个规则获取内容
     */
    private List<String> getResultLast(Elements elements, String lastRule) {
        List<String> textS = new ArrayList<>();
        try {
            switch (lastRule) {
                case "text":
                    for (Element element : elements) {
                        String text = element.text();
                        textS.add(text);
                    }
                    break;
                case "ownText":
                    List<String> keptTags = Arrays.asList("br", "b", "em", "strong");
                    for (Element element : elements) {
                        Element ele = element.clone();
                        for (Element child : ele.children()) {
                            if (!keptTags.contains(child.tagName())) {
                                child.remove();
                            }
                        }
                        String[] htmlS = ele.html().replaceAll("(?i)<br[\\s/]*>", "\n")
                                .replaceAll("<.*?>", "").split("\n");
                        for (String temp : htmlS) {
                            temp = FormatWebText.getContent(temp);
                            if (!TextUtils.isEmpty(temp)) {
                                textS.add(temp);
                            }
                        }
                    }
                    break;
                case "textNodes":
                    for (Element element : elements) {
                        List<TextNode> contentEs = element.textNodes();
                        for (int i = 0; i < contentEs.size(); i++) {
                            String temp = contentEs.get(i).text().trim();
                            temp = FormatWebText.getContent(temp);
                            if (!isEmpty(temp)) {
                                textS.add(temp);
                            }
                        }
                    }
                    break;
                case "html":
                    elements.select("script").remove();
                    String html = elements.html();
                    String[] htmlS = html.replaceAll("(?i)<(br[\\s/]*|p.*?|div.*?|/p|/div)>", "\n")
                            .replaceAll("<.*?>", "")
                            .split("\n");
                    for (String temp : htmlS) {
                        temp = FormatWebText.getContent(temp);
                        if (!isEmpty(temp)) {
                            textS.add(temp);
                        }
                    }
                    break;
                default:
                    for (Element element : elements) {
                        String url = NetworkUtil.getAbsoluteURL(baseURL, element.attr(lastRule));
                        if (!TextUtils.isEmpty(url) && !textS.contains(url)) {
                            textS.add(url);
                        }
                    }
            }
        } catch (Exception ignore) {
        }
        return textS;
    }

}

