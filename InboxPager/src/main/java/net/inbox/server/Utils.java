/**
 * InboxPager, an android email client.
 * Copyright (C) 2016  ITPROJECTS
 * <p/>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package net.inbox.server;

import android.util.Base64;

import net.inbox.Pager;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    // Types of MIME data
    private static String t_composite =
            "ALTERNATIVE|DIGEST|ENCRYPTED|FORM DATA|MESSAGE|MIXED|RELATED|REPORT|SIGNED";

    private static Pattern pat;
    private static Matcher mat;

    /**
     * Parses IMAP bodystructure into java objects.
     **/
    public static ArrayList<String[]> imap_parse_bodystructure(String buff) {
        String msg_body;

        pat = Pattern.compile(".*BODY\\w{0,9}\\s(.*)\\)", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(buff.trim());
        if (mat.matches()) {
            msg_body = mat.group(1);
            if (msg_body.trim().length() < 5) return null;
        } else return null;

        // Array values correspond to index (1 or 1.1), and contents
        ArrayList<String[]> structure = imap_parse_one_level(new String[] { "?", msg_body });

        boolean loop_complete = false;
        boolean sub_levels;
        while (!loop_complete) {
            sub_levels = false;
            int change_index = -1;
            int sz = structure.size();
            for (int i = 0;i < sz;++i) {
                sub_levels = structure.get(i)[1].startsWith("(");
                if (sub_levels) {
                    change_index = i;
                    break;
                }
            }
            if (change_index > -1) {
                ArrayList<String[]> sub_arr = imap_parse_one_level(structure.get(change_index));
                structure.remove(change_index);
                structure.addAll(change_index, sub_arr);
            } else if (!sub_levels) {
                loop_complete = true;
            }
        }

        return structure;
    }

    /**
     * Parses IMAP bodystructure element into sub-elements.
     **/
    private static ArrayList<String[]> imap_parse_one_level(String[] txt) {
        ArrayList<String[]> structure = new ArrayList<>();
        if (txt[1].charAt(txt[1].length() - 1) == ')') {
            txt[1] = txt[1].substring(1, txt[1].length() - 1);
            if (txt[1].trim().charAt(0) != '(') {
                structure.add(new String[] { "1", txt[1] });
                return structure;
            }
        }

        ArrayList<Integer> char_positions = new ArrayList<>();
        StringBuilder sb_between = new StringBuilder();

        // Separating the parts of the message
        for (int i = 0;i < txt[1].length();++i) {
            if (txt[1].charAt(i) == '(') {
                char_positions.add(i);
                continue;
            } else if (txt[1].charAt(i) == ')') {
                int begin = char_positions.get(char_positions.size() - 1);
                char_positions.remove(char_positions.get(char_positions.size() - 1));
                if (char_positions.size() == 0) {
                    structure.add(new String[] { "-1",  txt[1].substring(begin + 1, i) });
                }
                continue;
            } else if (txt[1].charAt(i) == '\"' && txt[1].charAt(i - 1) == ' ') {
                if (txt[1].charAt(i - 2) == '\"') {
                    pat = Pattern.compile("\\(\"(\\w{0,5}NAME)\".*", Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(txt[1].substring(char_positions.get(char_positions.size() - 1), i));
                    if (mat.matches()) {
                        int sub_index = mat.group(1).equalsIgnoreCase("name") ?
                                txt[1].indexOf("\") ", i) : txt[1].indexOf("\")) ", i);
                        if (sub_index > 0) i = sub_index;
                    }
                }
            }
            if (char_positions.size() == 0) {
                sb_between.append(txt[1].charAt(i));
            }
        }

        // Testing multipart subtype
        String s_type = sb_between.toString().trim();
        pat = Pattern.compile("\"(" + t_composite + ")\"(.*)", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(s_type);
        if (mat.matches()) {
            structure.add(0, new String[] { "-1" , "\"" + mat.group(1) + "\"" });
            if (!mat.group(2).trim().isEmpty()) {
                structure.remove(structure.size() - 1);
            }
        }

        String t_discrete = "APPLICATION|AUDIO|IMAGE|TEXT|VIDEO";

        // Removing some entries
        for (int i = structure.size() - 1; i >= 0;i--) {
            String str_val = structure.get(i)[1].trim();
            if (str_val.matches("\\(\".*")) {
                String pattern_subtype = "\\(\"(" + t_composite + "|" + t_discrete + ")\".*";
                pat = Pattern.compile(pattern_subtype, Pattern.CASE_INSENSITIVE);
                mat = pat.matcher(str_val);
                if (!mat.matches()) structure.remove(i);
            }
        }

        // Index numbering, i.e. 0, 1, 1.1, 1.2, 2 ...
        if (txt[0].equals("?")) {
            int i = 0;
            if (structure.size() > 1) {
                //for (String[] t : structure) {
                for (int j = 0;j < structure.size();++j) {
                    if (j == 0) {
                        structure.get(j)[0] = "0";
                    } else {
                        structure.get(j)[0] = String.valueOf(++i);
                    }
                }
            } else if (structure.size() == 1) {
                structure.get(0)[0] = "1";
            }
        } else {
            int i = 0;
            int ii = 0;
            for (String[] t : structure) {
                if (t[1].matches("\"(" + t_composite + ")\".*")) {
                    t[0] = txt[0].equals("-1") ? (txt[0] + "." + String.valueOf(++i)) : txt[0];
                } else {
                    t[0] = txt[0] + "." + String.valueOf(++ii);
                }
            }
        }

        return structure;
    }

    /**
     * Finds text nodes, prepares the attachments.
     **/
    public static ArrayList<String[]> imap_parse_nodes(ArrayList<String[]> structure,
        String[] arr_texts_plain, String[] arr_texts_html, String[] arr_texts_other) {
        if (structure == null) return null;
        if (structure.size() == 1) {
            imap_parse_text_params(structure.get(0), arr_texts_plain, arr_texts_html,
                    arr_texts_other);
            return null;
        }

        ArrayList<String[]> texts = new ArrayList<>();
        if (structure.get(0)[1].matches("\"ALTERNATIVE\".*")) {
            // All MIME nodes should be text/* formatted
            for (int i = 1;i < structure.size();++i) {
                if (structure.get(i)[1].matches("\"TEXT\".*")) texts.add(structure.get(i));
            }
            structure.clear();
            for (String[] t : texts) {
                imap_parse_text_params(t, arr_texts_plain, arr_texts_html, arr_texts_other);
            }
        } else {
            Iterator<String[]> texts_iterator = structure.iterator();
            while (texts_iterator.hasNext()) {
                String[] txt_tmp = texts_iterator.next();
                if (txt_tmp[0].matches("(0|1).*")) {
                    pat = Pattern.compile("\"(" + t_composite + ")\".*", Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(txt_tmp[1]);
                    if (mat.matches()) {
                        texts_iterator.remove();
                    } else {
                        pat = Pattern.compile("\"TEXT\".*", Pattern.CASE_INSENSITIVE);
                        mat = pat.matcher(txt_tmp[1]);
                        if (mat.matches()) {
                            imap_parse_text_params(txt_tmp, arr_texts_plain, arr_texts_html,
                                    arr_texts_other);
                            texts_iterator.remove();
                        }
                    }
                }
            }
        }

        // Remaining items are attachments
        ArrayList<String[]> return_structure = new ArrayList<>();
        if (structure.size() > 0) {
            for (int j = 0;j < structure.size();++j) {
                return_structure.add(j, imap_parse_attachment_params(structure.get(j)));
            }
        }

        return return_structure;
    }

    /**
     * Parses text nodes. PLAIN, HTML, OTHER.
     * PLAIN, 1.1, UTF-8, QUOTED-PRINTABLE
     **/
    private static void imap_parse_text_params(String[] str_txt,
        String[] arr_texts_plain, String[] arr_texts_html, String[] arr_texts_other) {

        // Mime subtype
        String mime_subtype;
        pat = Pattern.compile("\"TEXT\" \"(\\w+)\" .*", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(str_txt[1]);
        if (mat.matches()) {
            // IF the node is a text
            mime_subtype = mat.group(1).toUpperCase();

            // Get text charset and transfer encoding
            String charset = "";
            String transfer_enc = "";
            pat = Pattern.compile(".*\"CHARSET\" \"(.*)", Pattern.CASE_INSENSITIVE);
            mat = pat.matcher(str_txt[1]);
            if (mat.matches()) {
                int n = mat.group(1).indexOf("\"", 0);
                if (n >= 0) {
                    charset = mat.group(1).substring(0, n).toUpperCase();
                    pat = Pattern.compile(".* \"(7BIT|8BIT|BINARY|BASE64|QUOTED-PRINTABLE)\" .*",
                            Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(str_txt[1]);
                    if (mat.matches()) {
                        transfer_enc = mat.group(1);
                    } else {
                        transfer_enc = "8BIT";
                    }
                }
            }
            switch (mime_subtype) {
                case "PLAIN":
                    arr_texts_plain[0] = "PLAIN";
                    arr_texts_plain[1] = str_txt[0];
                    arr_texts_plain[2] = charset;
                    arr_texts_plain[3] = transfer_enc;
                    break;
                case "HTML":
                    arr_texts_html[0] = "HTML";
                    arr_texts_html[1] = str_txt[0];
                    arr_texts_html[2] = charset;
                    arr_texts_html[3] = transfer_enc;
                    break;
                default:
                    // OTHER
                    if (arr_texts_other[0].equals("-1")) {
                        arr_texts_other[0] = str_txt[0];
                    } else {
                        arr_texts_other[0] += "," + str_txt[0];
                    }
                    break;
            }
        }
    }

    /**
     * Parse attachments nodes.
     * UID, MIME-Type/subtype, name/filename, encoding, size.
     **/
    private static String[] imap_parse_attachment_params(String[] str_attach) {
        String type = "", name = "", encoding = "", size = "";
        int ii = 0;
        for (int i = 0;i < str_attach[1].length();++i) {
            if (str_attach[1].charAt(i) == '\"') {
                ++ii;
                if (ii == 2) type += "/";
            } else {
                if (ii == 1 || ii == 3) {
                    type += Character.toString(str_attach[1].charAt(i));
                }
            }
        }

        String temp = str_attach[1].substring(type.length() + 5);

        // No name parameter
        pat = Pattern.compile("nil .*", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(temp);
        boolean no_name = mat.matches();
        if (no_name) {
            temp = temp.substring(4);
        } else {
            // Name parameter
            int sub_index = temp.indexOf("\" ", 1);
            String type_of_name = "";
            if (sub_index > 0) {
                type_of_name = temp.substring(2, sub_index);
                temp = temp.substring(type_of_name.length() + 4);
            }
            if (type_of_name.endsWith("*")) {
                sub_index = temp.indexOf(") ", 1);
                if (sub_index > 0) {
                    name = temp.substring(0, sub_index);
                    temp = temp.substring(name.length() + 2);
                }
            } else {
                sub_index = temp.indexOf("\") ", 1);
                if (sub_index > 0) {
                    name = temp.substring(1, sub_index);
                    temp = temp.substring(name.length() + 4);
                }
            }
        }

        ii = 0;
        String tmp = "";
        for (int j = 0;j < temp.length();++j) {
            if (ii == 4) {
                temp = temp.substring(j);
                break;
            }
            if (temp.charAt(j) != ' ' && temp.charAt(j) != '(') {
                if (temp.charAt(j) == '\"') {
                    int sub_index = temp.indexOf("\" ", j + 1);
                    if (sub_index > 0) {
                        tmp = temp.substring(j + 1, sub_index);
                        if (ii == 2) {
                            encoding = tmp;
                        } else if (ii == 3) {
                            size = tmp;
                        }
                        tmp = "";
                        j = sub_index + 1;
                        ++ii;
                    }
                } else {
                    tmp += Character.toString(temp.charAt(j));
                }
            } else if (temp.charAt(j) == ' ') {
                if (ii == 2) {
                    encoding = tmp;
                } else if (ii == 3) {
                    size = tmp;
                }
                tmp = "";
                ++ii;
            }
        }

        // BASE64 OR Quoted-printable
        pat = Pattern.compile(".*\\(\"ATTACHMENT\" \\(\"FILENAME\" \"(.*)\"\\)\\) .*",
                Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(temp);
        if (mat.matches()) name = mat.group(1);

        // URL-encoded filename
        pat = Pattern.compile(".*\\(\"ATTACHMENT\" \\(\"FILENAME\\*\" (.*)\\)\\) .*",
                Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(temp);
        if (mat.matches()) {
            name = mat.group(1);
            pat = Pattern.compile(".*\\}(.*)", Pattern.CASE_INSENSITIVE);
            mat = pat.matcher(name);
            if (mat.matches()) {
                temp = mat.group(1);
                for (int k = 0;k < temp.length();++k) {
                    if (temp.charAt(k) == '}') temp = temp.substring(k);
                }
                name = content_disposition_name(false, temp);
            }
        } else {
            //B64|QP
            Utils.parse_line_B64_QP(name);
        }

        // Message attachments, (uid, mime-type, name, transfer-encoding, size)
        return new String[] { str_attach[0], type, name, encoding, size };
    }

    /**
     * Parses MIME bodystructure.
     **/
    public static ArrayList<String[]> mime_bodystructure(String buff, String boundary) {
        // Array values correspond to index (1 or 1.1), and contents
        ArrayList<String[]> structure = new ArrayList<>();

        ArrayList<String> lines = new ArrayList<>();
        ArrayList<String> boundaries = new ArrayList<>();
        boundaries.add(boundary);

        String tmp = "";
        boolean semi = false;
        Pattern pt1 = Pattern.compile("Content-.*:.*", Pattern.CASE_INSENSITIVE);
        Pattern pt2 = Pattern.compile(".*boundary=(.*)", Pattern.CASE_INSENSITIVE);
        int sz = buff.length();
        for (int i = 0;i < sz;++i) {
            if (buff.charAt(i) == '\n') {
                if (tmp.startsWith("--")) {
                    // Boundary check
                    for (String str : boundaries) {
                        if (tmp.startsWith(str)) lines.add(tmp);
                    }
                } else {
                    // Next line Content-Type
                    if (pt1.matcher(tmp).matches() && (tmp.trim().length() <= 15)) {
                        semi = true;
                        continue;
                    }
                    if (semi) {
                        if (lines.size() > 0) {
                            lines.set(lines.size() - 1, (lines.get(lines.size() - 1) + tmp));
                        }
                        mat = pt2.matcher(tmp);
                        if (mat.matches())
                            boundaries.add("--" + mat.group(1).replaceAll("\"", "").trim());
                    }
                    if (pt1.matcher(tmp).matches()) {
                        lines.add(tmp);
                        semi = tmp.trim().matches(".*;");
                    } else {
                        semi = false;
                    }
                }
                tmp = "";
            } else if (buff.charAt(i) != '\r' && buff.charAt(i) != '\t') {
                tmp += Character.toString(buff.charAt(i));
            }
        }
        if (!tmp.isEmpty()) lines.add(tmp);

        // Top level structure
        ArrayList<String> nodes = new ArrayList<>();
        Pattern pt3 = Pattern.compile("Content-Type:.*multipart/.*", Pattern.CASE_INSENSITIVE);
        int level = 0;
        boolean multipart;
        boolean complete = true;
        while (complete) {
            for (String st : lines) {
                if (st.startsWith(boundary)) {
                    if (nodes.size() > 0) {
                        // Multipart test
                        multipart = false;
                        for (String t : nodes) {
                            mat = pt3.matcher(t);
                            if (mat.matches()) multipart = true;
                        }
                        if (multipart) {
                            String m = "";
                            for (String nd : nodes) { m += nd + "\n"; }
                            structure.addAll(mime_parse_node_multipart(String.valueOf(++level), m));
                        } else {
                            structure.add(mime_parse_node(String.valueOf(++level),
                                    boundaries.get(0), nodes));
                        }
                        nodes.clear();
                    }
                } else {
                    nodes.add(st);
                }
            }

            // Test for remaining multipart
            boolean loop = false;
            for (String[] t_str : structure) {
                if (t_str[1].matches("multipart.*")) {
                    loop = true;
                    break;
                }
            }
            complete = loop;
        }

        return structure;
    }

    /**
     * Parses MIME bodystructure into java objects.
     **/
    private static ArrayList<String[]> mime_parse_node_multipart(String num, String s_array) {
        ArrayList<String[]> structure = new ArrayList<>();
        String[] arr = s_array.split("\n");
        String bound = "";

        pat = Pattern.compile("Content-Type:.*multipart/.*;.*boundary=(.*)",
                Pattern.CASE_INSENSITIVE);

        for (int i = 0;i < arr.length;++i) {
            mat = pat.matcher(arr[i]);
            if (mat.matches()) {
                bound = "--" + mat.group(1).replaceAll("\"", "").trim();
                arr[i] = "";
                break;
            }
        }

        ArrayList<String> nodes = new ArrayList<>();
        Pattern pt1 = Pattern.compile("Content-Type:.*multipart/.*", Pattern.CASE_INSENSITIVE);
        boolean multipart;
        int level = 0;
        for (String st : arr) {
            if (st.startsWith(bound)) {
                if (nodes.size() > 0) {
                    // Multipart test
                    multipart = false;
                    for (String t : nodes) {
                        mat = pt1.matcher(t);
                        if (mat.matches()) multipart = true;
                    }
                    if (multipart) {
                        String m = "";
                        for (String nd : nodes) { m += nd + "\n"; }
                        structure.add(new String[] { num + "." + String.valueOf(++level),
                                "multipart/", m });
                    } else {
                        structure.add(mime_parse_node(num + "." + String.valueOf(++level),
                                bound, nodes));
                    }
                    nodes.clear();
                }
            } else {
                if (!st.isEmpty()) nodes.add(st);
            }
        }

        return structure;
    }

    /**
     * Parses MIME bodystructure element into sub-elements.
     **/
    private static String[] mime_parse_node(String num, String bounds, ArrayList<String> n_array) {
        String[] ret = new String[] { num, "", bounds, "", "", "", "" };

        boolean b_content_type = false;
        boolean b_content_transfer = false;
        boolean b_content_disposition = false;

        String tmp_chk;
        String content_disposition = "";

        Pattern pt1 = Pattern.compile("Content-Type:(.*);.*", Pattern.CASE_INSENSITIVE);
        Pattern pt2 = Pattern.compile("Content-Type:(.*)", Pattern.CASE_INSENSITIVE);
        Pattern pt3 = Pattern.compile(".*(name|name\\*)=(.*)", Pattern.CASE_INSENSITIVE);
        Pattern pt4 = Pattern.compile(".*charset=(.*)", Pattern.CASE_INSENSITIVE);

        for (String t : n_array) {
            tmp_chk = t.trim().toLowerCase();
            if (tmp_chk.startsWith("content-type:") && !b_content_type) {
                // Get mime-type
                mat = pt1.matcher(t);
                if (mat.matches()) {
                    ret[1] = mat.group(1).trim();
                } else {
                    mat = pt2.matcher(t);
                    if (mat.matches()) ret[1] = mat.group(1).trim();
                }

                // Get name
                mat = pt3.matcher(t);
                if (mat.matches()) {
                    ret[3] = mat.group(2).trim().replaceAll("\"", "");
                }

                // Get text charset
                mat = pt4.matcher(t);
                if (mat.matches()) {
                    ret[5] = mat.group(1).trim().replaceAll("\"", "").toUpperCase();
                }
                b_content_type = true;
            } else if (tmp_chk.startsWith("content-transfer-encoding:") && !b_content_transfer) {
                // Get transfer encoding
                pat = Pattern.compile("(.*)", Pattern.CASE_INSENSITIVE);
                mat = pat.matcher(t.substring(26).trim().toUpperCase());
                if (mat.matches()) ret[4] = mat.group(1).trim();
                b_content_transfer = true;
            } else if (tmp_chk.startsWith("content-disposition:") && !b_content_disposition) {
                // Get attachment filename
                pat = Pattern.compile("(.*);(.*)(filename|filename\\*)=(.*)", Pattern.CASE_INSENSITIVE);
                mat = pat.matcher(content_disposition);
                if (mat.matches()) ret[3] = mat.group(4).trim();
                b_content_disposition = true;
            }
        }

        // From B64, QP, or URL to text
        if (ret[3].length() > 0) {
            if (validate_B64_QP(ret[3])) {
                ret[3] = split_B64_QP(ret[3]);
            } else {
                ret[3] = content_disposition_name(false, ret[3]);
            }
        }

        return ret;
    }

    /**
     * Gets mime part from mime message.
     * Full message, uid part, message boundary, text boundary.
     **/
    public static String mime_part_section(String s, String uid, String uid_boundary) {
        // Converting uid from 1.3 to 3
        int n_uid = uid.lastIndexOf('.');
        if (n_uid == -1) {
            n_uid = Integer.parseInt(uid);
        } else {
            n_uid = Integer.parseInt(uid.substring(n_uid + 1));
        }

        boolean b = true;
        int count = 0;
        int current_indx = 0;
        while (b) {
            current_indx = s.indexOf(uid_boundary, current_indx);
            if (current_indx == -1) {
                b = false;
            } else {
                ++count;
                if (count == n_uid) {
                    b = false;
                } else {
                    current_indx += uid_boundary.length();
                }
            }
        }

        if (current_indx == -1) return "!";
        String str = s.substring(current_indx);

        // Removing top boundary
        str = str.substring((uid_boundary.length() + 2));

        boolean content;
        boolean semi = false;
        int mime_hdr;
        pat = Pattern.compile("Content-.*", Pattern.CASE_INSENSITIVE);
        String tmp = "";
        String[] s_arr = str.split("\r\n");
        for (String s_tmp : s_arr) {
            content = pat.matcher(s_tmp).matches();
            if (content) {
                semi = tmp.replaceAll("\r", "").replaceAll("\n", "").replaceAll("\t", "")
                        .replaceAll(" ", "").endsWith(";");
                tmp += s_tmp + "\r\n";
            } else {
                if (semi) {
                    tmp += s_tmp + "\r\n";
                } else {
                    break;
                }
            }
        }

        mime_hdr = tmp.length();

        int end_indx = str.indexOf(uid_boundary, uid_boundary.length());
        if (end_indx == -1) {
            str =  "!";
        } else {
            str = str.substring(mime_hdr, end_indx);
        }

        // Removing extra new line characters
        if (str.length() >= 3 && str.charAt(0) == '\n' && str.charAt(str.length() - 1) == '\n') {
            str = str.substring(1, str.length() - 1);
        }

        return str;
    }

    public static boolean validate_B64_QP(String s) {
        return !s.isEmpty() && (s.contains("=?") && s.contains("?="));
    }

    public static String split_B64_QP(String s) {
        String ret = "";
        StringBuilder sb = new StringBuilder();
        boolean in_bracket = false;
        for (int i = 0;i < s.length();++i) {
            if (s.charAt(i) == '=' && (i + 1) <= (s.length() - 1)) {
                if (s.charAt(i + 1) == '=') continue;
                if (s.charAt(i + 1) == '?' && !in_bracket) {
                    if (sb.length() > 0) {
                        ret += parse_line_B64_QP(sb.toString());
                        sb.setLength(0);
                    }
                    sb.append(s.charAt(i));
                    sb.append(s.charAt(++i));
                    in_bracket = true;
                } else {
                    sb.append(s.charAt(i));
                }
            } else if (s.charAt(i) == '?' && i < (s.length() - 1)) {
                sb.append(s.charAt(i));
                if (s.charAt(i + 1) == '=') {
                    sb.append(s.charAt(++i));
                    in_bracket = false;
                    ret += parse_line_B64_QP(sb.toString());
                    sb.setLength(0);
                } else if (s.charAt(i + 1) == 'B' || s.charAt(i + 1) == 'b'
                        || s.charAt(i + 1) == 'Q' || s.charAt(i + 1) == 'q') {
                    sb.append(s.charAt(++i));
                    sb.append(s.charAt(++i));
                }
            } else {
                sb.append(s.charAt(i));
            }
        }

        if (sb.length() > 0) {
            ret += sb.toString();
            sb.setLength(0);
        }
        return ret;
    }

    /**
     * Converts from BASE64 to text. Kkg1YTQtdC= -> Text 1 contents.
     **/
    public static String parse_BASE64(String s) {
        String ret = "";
        String[] st = s.split("\r\n");
        for (String tmp : st) {
            ret += new String(Base64.decode(tmp.replaceAll("\r", "").replaceAll("\n", "")
                    .replaceAll("=", ""), Base64.DEFAULT));
        }
        return ret;
    }

    /**
     * Decides appropriate non-ascii string encoding.
     * Converts from BASE64 or Quoted Printable to UTF-8.
     *
     * i.e.: (not a real word)
     * =?utf-8?B?Kkg1YTQtdC=?=
     * =?utf-8?Q?=D6=93=D4=BE=D1?=
     * =?charset?encoding?encoded-text?=
     **/
    public static String parse_line_B64_QP(String s) {
        s = s.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\t", "");
        pat = Pattern.compile("=\\?(.*)\\?(\\w)\\?(.*)\\?=", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(s);
        if (mat.matches()) {
            if (mat.group(2).matches("(B|b)")) {
                if (s.isEmpty()) return "";
                byte[] arr_bytes = Base64.decode(mat.group(3).replaceAll("=", ""), Base64.DEFAULT);
                try {
                    return new String((new String(arr_bytes, mat.group(1))).getBytes(), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    System.out.println("Exception: " + e.getMessage());
                    Pager.log += "!!:" + e.getMessage() + "\n";
                    return s;
                }
            } else if (mat.group(2).matches("(Q|q)")) {
                if (mat.group(3).isEmpty()) {
                    return "";
                } else {
                    return parse_quoted_printable(mat.group(3), mat.group(1));
                }
            } else {
                return s;
            }
        } else return s;
    }

    /**
     * Convert an ASCII Quoted Printable to UTF-8 string.
     *
     * i.e.: (not a real word)
     * =?utf-8?Q?=D6=93=D4=BE=D1?=
     * =?charset?encoding?encoded-text?=
     **/
    public static String parse_quoted_printable(String s, String encoding) {
        String s_tmp;
        try {
            s_tmp = new String(s.getBytes(), encoding);
        } catch (UnsupportedEncodingException e) {
            System.out.println("Exception: " + e.getMessage());
            Pager.log += "!!:" + e.getMessage() + "\n";
            return s;
        }
        if (s_tmp.endsWith("=")) s_tmp = s_tmp.substring(0, s_tmp.length() - 1);
        String str;
        int len = s_tmp.length();
        byte[] ascii_bytes = new byte[s_tmp.length()];
        int count = 0;
        for (int i = 0;i < len;++i) {
            if (s_tmp.charAt(i) == '=' && (i + 2) <= (len - 1)) {
                if (s_tmp.charAt(i + 1) == '\r' && s_tmp.charAt(i + 2) == '\n') {
                    // Skip this line
                    i += 2;
                } else {
                    try {
                        str = Character.toString(s_tmp.charAt(i + 1))
                                + Character.toString(s_tmp.charAt(i + 2));
                        ascii_bytes[count] = (byte) Integer.parseInt(str, 16);
                    } catch (Exception e) {
                        continue;
                    }
                    ++count;
                    i += 2;
                }
            }  else {
                ascii_bytes[count] = (byte) s_tmp.charAt(i);
                ++count;
            }
        }
        try {
            byte[] reduced = new byte[count];
            System.arraycopy(ascii_bytes, 0, reduced, 0, count);
            return new String(reduced, encoding);
        } catch (UnsupportedEncodingException e) {
            System.out.println("Exception: " + e.getMessage());
            Pager.log += "!!:" + e.getMessage() + "\n";
            return s;
        }
    }

    /**
     * Converts (encode-decode) URL-style filenames for transmission.
     *
     * filename*=UTF-8''Na%C3%AFve%20file.txt
     **/
    public static String content_disposition_name(boolean encode, String filename) {
        if (encode) {
            try {
                return "UTF-8''" + URLEncoder.encode(filename, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return filename;
            }
        } else {
            try {
                pat = Pattern.compile("(.*)'(.*)'(.*)", Pattern.CASE_INSENSITIVE);
                mat = pat.matcher(filename);
                if (mat.matches()) {
                    if (mat.group(1).equalsIgnoreCase("UTF-8")) {
                        return URLDecoder.decode(mat.group(3), mat.group(1));
                    } else {
                        return new String(URLDecoder.decode(mat.group(3), mat.group(1))
                                .getBytes(), "UTF-8");
                    }
                } else {
                    return filename;
                }
            } catch (UnsupportedEncodingException e) {
                System.out.println("Exception: " + e.getMessage());
                Pager.log += "!!:" + e.getMessage() + "\n";
                return filename;
            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
                Pager.log += "!!:" + e.getMessage() + "\n";
                return filename;
            }
        }
    }

    public static String to_ascii(String s) {
        try {
            return new String(s.getBytes("US-ASCII"));
        } catch (UnsupportedEncodingException enc) {
            System.out.println("Exception: " + enc.getMessage());
            Pager.log += "!!:" + enc.getMessage() + "\n";
        }
        return s;
    }

    public static boolean all_ascii(String s) {
        for (char c: s.toCharArray()) { if (((int) c) > 127) return false; }
        return true;
    }

    public static String to_base64_utf8(String s) {
        return "=?utf-8?B?" + Base64.encodeToString(s.getBytes(), Base64.NO_WRAP) + "?=";
    }
}
