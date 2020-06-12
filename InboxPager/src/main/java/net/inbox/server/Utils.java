/*
 * InboxPager, an android email client.
 * Copyright (C) 2016-2020  ITPROJECTS
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

import android.annotation.SuppressLint;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Base64;

import net.inbox.InboxPager;
import net.inbox.db.Message;

import org.apache.commons.codec.QuotedPrintableCodec;
import org.apache.commons.fileupload.util.mime.MimeUtility;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static Matcher mat;
    private static Pattern pat;

    // Types of MIME data
    private static String t_composite =
            "ALTERNATIVE|DIGEST|ENCRYPTED|FORM DATA|MESSAGE|MIXED|RELATED|REPORT|SIGNED";

    /**
     * Parses IMAP bodystructure into java objects.
     **/
    static ArrayList<String[]> imap_parse_bodystructure(String buff) {
        String msg_body;

        pat = Pattern.compile(".*BODY\\w{0,9}\\s(.*)\\)", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(buff.trim());
        if (mat.matches()) {
            msg_body = mat.group(1);
            if (msg_body != null && msg_body.trim().length() < 5) return null;
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
                        int sub_index = mat.group(1).equalsIgnoreCase("NAME") ?
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
                    t[0] = txt[0].equals("-1") ? (txt[0] + "." + (++i)) : txt[0];
                } else {
                    t[0] = txt[0] + "." + (++ii);
                }
            }
        }

        return structure;
    }

    /**
     * Finds text nodes, prepares the attachments.
     **/
    static ArrayList<String[]> imap_parse_nodes(ArrayList<String[]> structure,
        String[] arr_texts_plain, String[] arr_texts_html) {

        if (structure == null) return null;
        if (structure.size() == 1) {
            imap_parse_text_params(structure.get(0), arr_texts_plain, arr_texts_html);
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
                imap_parse_text_params(t, arr_texts_plain, arr_texts_html);
            }
        } else {
            Iterator<String[]> texts_iterator = structure.iterator();
            while (texts_iterator.hasNext()) {
                String[] txt_tmp = texts_iterator.next();
                if (txt_tmp[0].matches("([01]).*")) {
                    pat = Pattern.compile("\"(" + t_composite + ")\".*", Pattern.CASE_INSENSITIVE);
                    mat = pat.matcher(txt_tmp[1]);
                    if (mat.matches()) {
                        texts_iterator.remove();
                    } else {
                        pat = Pattern.compile("\"TEXT\".*", Pattern.CASE_INSENSITIVE);
                        mat = pat.matcher(txt_tmp[1]);
                        if (mat.matches()) {
                            imap_parse_text_params(txt_tmp, arr_texts_plain, arr_texts_html);
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
        String[] arr_texts_plain, String[] arr_texts_html) {

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
                int n = mat.group(1).indexOf("\"");
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
                if (ii == 2) type = type.concat("/");
            } else {
                if (ii == 1 || ii == 3) {
                    type = type.concat(Character.toString(str_attach[1].charAt(i)));
                }
            }
        }

        String temp;
        try {
            temp = str_attach[1].substring(type.length() + 5);
        } catch (Exception e) {
            InboxPager.log += "!!7:" + e.getMessage() + "\n\n";
            return new String[] { str_attach[0], type, "???", "UTF-8?", "-1" };
        }

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
            if (name != null)
                name = content_disposition_name(false, name.substring(name.indexOf("}")));
        } else {
            // Convert encoded-word from RFC 2047 to text.
            Utils.parse_encoded_word(name);
        }

        // Message attachments, (uid, mime-type, name, transfer-encoding, size)
        return new String[] { str_attach[0], type, name, encoding, size };
    }

    private static class MIME {

        MIME() {}

        Boolean multipart = false;
        String charset = "";
        String boundary = "";
        String mime_type = "";
        String name = "";
        String type = "";
        String transfer_encoding = "";
        String disposition = "";
        String description = "";
        String size = "";
        String index = "";
        String[] sequence = null;

        String[] toArray() {
            return new String[]
                    { index, mime_type, boundary, name, transfer_encoding, charset, size };
        }
    }

    /**
     * Parses MIME bodystructure.
     * buff mime part of the email,
     * m_boundary top mime boundary,
     * m_type content type of the mime
     **/
    public static ArrayList<String[]> mime_bodystructure(String buff, String m_boundary,
                                                         String m_type) {
        ArrayList<String> boundaries = new ArrayList<>();
        boundaries.add(m_boundary);

        // Finding the boundaries
        Pattern pat_border = Pattern.compile(".*boundary=\"(.*)\".*",
                Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);
        mat = pat_border.matcher(buff);
        while (mat.find()) { boundaries.add("--" + mat.group(1)); }

        String[] lines = buff.replaceAll("\r", "").split("\n");

        ArrayList<MIME> parts = new ArrayList<>();

        MIME item = new MIME();

        boolean multiline = false;
        boolean multi_match;

        // 1 type 2 transfer-encoding 3 disposition 4 description
        int content_prop = 0;
        pat = Pattern.compile("Content-(.*):(.*)", Pattern.CASE_INSENSITIVE);
        for (String t : lines) {
            // Boundary check
            if (t.startsWith("--")) {
                for (String bn : boundaries) {
                    if (t.startsWith(bn) && !t.endsWith(bn + "--")) {
                        parts.add(item);
                        item = new MIME();
                        item.boundary = t.trim();
                    }
                }
            }

            mat = pat.matcher(t);
            multi_match = mat.matches();
            if (multiline || multi_match) {
                if (multi_match) {
                    multiline = mat.group(2).trim().endsWith(";");
                    switch (mat.group(1).toLowerCase()) {
                        case "type":
                            content_prop = 1;
                            item.type = mat.group(2).replaceAll("\r", "").trim();
                            if (item.type.toLowerCase().contains("multipart")) {
                                item.multipart = true;
                                mat = pat_border.matcher(item.type);
                            }
                            break;
                        case "transfer-encoding":
                            content_prop = 2;
                            item.transfer_encoding = mat.group(2).replaceAll("\r", "").trim();
                            break;
                        case "disposition":
                            content_prop = 3;
                            item.disposition = mat.group(2).replaceAll("\r", "").trim();
                            break;
                        case "description":
                            content_prop = 4;
                            item.description = mat.group(2).replaceAll("\r", "").trim();
                            break;
                    }
                } else {
                    // Not a Content-* line
                    switch (content_prop) {
                        case 1:
                            item.type += t.replaceAll("\r", "").trim();
                            multiline = item.type.endsWith(";");
                            break;
                        case 2:
                            item.transfer_encoding = item.transfer_encoding.concat(
                                    t.replaceAll("\r", "").trim());
                            multiline = item.transfer_encoding.endsWith(";");
                            break;
                        case 3:
                            item.disposition = item.disposition.concat(
                                    t.replaceAll("\r", "").trim());
                            multiline = item.disposition.endsWith(";");
                            break;
                        case 4:
                            item.description = item.description.concat(
                                    t.replaceAll("\r", "").trim());
                            multiline = item.description.endsWith(";");
                            break;
                    }
                }
            }
        }

        // Adding leftover item
        if (item.boundary.length() > 1) parts.add(item);

        parts.get(0).multipart = true;
        parts.get(0).type = m_type.substring(13).trim();

        // Mime part
        for (MIME p : parts) {
            p.sequence = new String[boundaries.size()];
        }

        int level = 0;
        for (int i = 0;i < boundaries.size();++i) {
            for (MIME p : parts) {
                if (p.boundary.equals(boundaries.get(i))) ++level;
                p.sequence[i] = String.valueOf(level);
            }
            level = 0;
        }

        // Removing wrong indices
        int index_bound;
        for (MIME p : parts) {
            index_bound = boundaries.indexOf(p.boundary);
            if (index_bound > -1) {
                for (int l = 0;l < p.sequence.length;++l) {
                    if (l > index_bound) {
                        p.sequence[l] = null;
                    } else {
                        if (l == 0) {
                            p.index = p.index.concat(String.valueOf(p.sequence[l]));
                        } else {
                            p.index = p.index.concat("." + (p.sequence[l]));
                        }
                    }
                }
            }
        }

        Pattern pt_name = Pattern.compile(".*(name|name\\*)=(.*)", Pattern.CASE_INSENSITIVE);
        Pattern pt_char = Pattern.compile(".*(charset|charset\\*)=(.*)", Pattern.CASE_INSENSITIVE);

        // Removing wrong mime parts
        for (int i = parts.size() - 1;i >= 0;i--) {
            if (parts.get(i).multipart) {
                parts.remove(parts.get(i));
            } else {
                // name
                if (parts.get(i).disposition.toLowerCase().contains("name")) {
                    mat = pt_name.matcher(parts.get(i).disposition);
                    if (mat.matches()) {
                        parts.get(i).name = mat.group(2).trim();
                        // From B64, QP, or URL to text
                        if (parts.get(i).name.length() > 0) {
                            if (is_encoded_word(parts.get(i).name)) {
                                parts.get(i).name = parse_encoded_word(parts.get(i).name);
                            } else {
                                parts.get(i).name = content_disposition_name(false, parts.get(i).name);
                            }
                            // Remove quotes
                            if (parts.get(i).name.startsWith("\"")
                                    && parts.get(i).name.endsWith("\"")) {
                                parts.get(i).name = parts.get(i).name.
                                        substring(1, parts.get(i).name.length() - 1);
                            }
                        }
                    }
                }
                // mime-type
                int nn = parts.get(i).type.indexOf(";");
                if (nn > -1) {
                    parts.get(i).mime_type = parts.get(i).type.substring(0, nn).trim();
                } else {
                    // mime-type is only property
                    parts.get(i).mime_type = parts.get(i).type.trim();
                }

                // charset
                mat = pt_char.matcher(parts.get(i).type);
                if (mat.matches()) {
                    parts.get(i).charset = mat.group(2).trim().replaceAll("\"", "");
                }
            }
        }

        // Rebuilding the structure
        ArrayList<String[]> structure = new ArrayList<>();
        for (MIME p : parts) { structure.add(p.toArray()); }

        return structure;
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
        int current_index = 0;
        while (b) {
            current_index = s.indexOf(uid_boundary, current_index);
            if (current_index == -1) {
                b = false;
            } else {
                ++count;
                if (count == n_uid) {
                    b = false;
                } else {
                    current_index += uid_boundary.length();
                }
            }
        }

        if (current_index == -1) return "!";
        String str = s.substring(current_index);

        // Removing top boundary
        str = str.substring((uid_boundary.length() + 1));

        int mime_hdr = 0;

        // Header removal, \n-vs-\r\n
        if (str.contains("\r\n")) {
            int xo = str.indexOf("\r\n\r\n");
            if (xo != -1) mime_hdr = xo + 4;
        } else {
            int xo = str.indexOf("\n\n");
            if (xo != -1) mime_hdr = xo + 2;
        }

        int end_index = str.indexOf(uid_boundary, uid_boundary.length());
        if (end_index == -1) {
            str =  "!";
        } else {
            str = str.substring(mime_hdr, end_index);
        }

        return str;
    }

    /**
     * Parses a MIME to set message texts.
     **/
    public static void mime_parse_full_msg_into_texts(String txt, ArrayList<String[]> msg_structure,
                                                      ArrayList<String[]> msg_texts, Message msg) {
        // Preparing texts
        boolean has_texts = false;
        ListIterator<String[]> iterate = msg_structure.listIterator();
        while (iterate.hasNext()) {
            String[] arr = iterate.next();
            if (arr[0].startsWith("1") && arr[1].startsWith("text/")) {
                if (!has_texts) has_texts = true;
                msg_texts.add(arr);
                iterate.remove();
            } else if (msg.get_content_type().toLowerCase()
                    .contains("multipart/alternative")
                    || (arr[0].startsWith("2") && !has_texts && arr[1].startsWith("text/"))) {
                msg_texts.add(arr);
                iterate.remove();
            }
        }

        String str = "";
        String hold = "";
        String txt_tmp;

        // Parsing texts
        for (String[] arr : msg_texts) {
            if (arr[1].startsWith("text/plain")) {
                msg.set_charset_plain(arr[5]);
                txt_tmp = Utils.mime_part_section(txt, arr[0], arr[2]);
                if (arr[4].equalsIgnoreCase("BASE64")) {
                    txt_tmp = Utils.parse_BASE64(txt_tmp);
                } else if (arr[4].equalsIgnoreCase("QUOTED-PRINTABLE")) {
                    txt_tmp = Utils.parse_quoted_printable(txt_tmp,
                            (arr[5].isEmpty() ? arr[5] : "UTF-8"));
                }
                msg.set_contents_plain(txt_tmp);
            } else if (arr[1].startsWith("text/html")) {
                msg.set_charset_html(arr[5]);
                txt_tmp = Utils.mime_part_section(txt, arr[0], arr[2]);
                if (arr[4].equalsIgnoreCase("BASE64")) {
                    for (int j = 0;j < txt_tmp.length();++j) {
                        if (txt_tmp.charAt(j) == '\n') {
                            str = str.concat(Utils.parse_BASE64(hold));
                        } else if (txt_tmp.charAt(j) != '\r' && txt_tmp.charAt(j) != '\t') {
                            hold += Character.toString(txt_tmp.charAt(j));
                        }
                    }
                    txt_tmp = str;
                    str = "";
                    hold = "";
                } else if (arr[4].equalsIgnoreCase("QUOTED-PRINTABLE")) {
                    txt_tmp = Utils.parse_quoted_printable(txt_tmp,
                            (arr[5].isEmpty() ? arr[5] : "UTF-8"));
                }
                msg.set_contents_html(txt_tmp);
            } else {
                msg.set_contents_other(Utils.mime_part_section(txt, arr[0], arr[2]));
            }
        }
    }

    /**
     * Given String message headers, put them in tags.
     **/
    static HashMap<String, String> parse_headers(String headers) {
        HashMap<String, String> tags = new HashMap<>();
        String received = "";
        int col;
        String tag = "";
        String test_tag = "";
        for (String line : headers.split("\n")) {
            if (line != null) {
                if (line.matches("([\t ]).*")) {
                    tag = tag.concat(" " + line.trim());
                } else {
                    col = tag.indexOf(":");
                    if (col > 0) {
                        test_tag = tag.substring(0, col + 1).toLowerCase();
                        if (test_tag.contains("received:")) {
                            received = received.concat("\n\n" + tag.substring(col + 1).trim());
                        } else {
                            tags.put(test_tag, tag.substring(col + 1).trim());
                        }
                    }
                    tag = line;
                }
            }
        }
        col = tag.indexOf(":");
        if (col > 0) {
            test_tag = tag.substring(0, col + 1).toLowerCase();
            tags.put(test_tag, tag.substring(col + 1).trim());
        }
        tags.put("received:", received);
        tags.remove("");
        return tags;
    }

    /**
     * Find content-type and boundary in given String.
     **/
    public static String[] content_type_boundary(String s) {
        Pattern pat = Pattern.compile("^content-type:\\s(.*?);.*", Pattern.CASE_INSENSITIVE);

        String sss = s.replaceAll("([\r\n\t])", "");

        String bounds = null;
        String content_type = null;
        String charset = "UTF-8";

        Matcher mat = pat.matcher(sss);
        if (mat.matches()) content_type = mat.group(1) + ";";

        pat = Pattern.compile("(.*?)boundary=\"(.*?)\".*", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(sss);
        if (mat.matches()) bounds = "--" + mat.group(2);

        pat = Pattern.compile(".*(charset=)(.*?)(;|$).*", Pattern.CASE_INSENSITIVE);
        mat = pat.matcher(sss);
        if (mat.matches()) charset = mat.group(2).toUpperCase().replaceAll("\"", "");

        return new String[]{ content_type, bounds, charset };
    }

    public static String boundary() {
        return ("=__" + UUID.randomUUID().toString().replaceAll("-","")
                .substring(0, 30));
    }

    /**
     * Checks given string for encoded-word (RFC 2047).
     **/
    static boolean is_encoded_word(String s) {
        return s!= null && !s.isEmpty() && (s.contains("=?") && s.contains("?="));
    }

    /**
     * Converts from BASE64 to text. SGVsbG8sIFdvcmxkIQo= -> Hello, World!.
     **/
    static String parse_BASE64(String b64_text) {
        String ret = "";
        String[] lines = b64_text.split("\r\n");
        for (String l : lines) {
            ret = ret.concat(new String(Base64.decode(
                    l.replaceAll("([\r\n=])", ""), Base64.DEFAULT)));
        }
        return ret;
    }

    static String parse_BASE64_encoded(String b64_text, String enc) {
        try {
            if (enc.equalsIgnoreCase("UTF-8") || enc.equals("-1")) {
                return new String(Base64.decode(
                        b64_text.replaceAll("([\r\n=])", ""), Base64.DEFAULT));
            } else {
                return new String(Base64.decode(
                        b64_text.replaceAll("([\r\n=])", ""), Base64.DEFAULT), enc);
            }
        } catch (UnsupportedEncodingException e) {
            InboxPager.log = InboxPager.log.concat("!B64!PARSE!" + e.getMessage() + "\n\n");
            return b64_text;
        }
    }

    /**
     * Encoded-word decoding.
     * Decides appropriate non-ascii string encoding.
     * Converts from BASE64 or Quoted Printable to UTF-8.
     *
     * i.e.: (not a real word)
     * =?utf-8?B?Kkg1YTQtdC=?=
     * =?utf-8?Q?=D6=93=D4=BE=D1?=
     * =?charset?encoding?encoded-text?=
     **/
    static String parse_encoded_word(String enc_word) {
        try {
            return MimeUtility.decodeText(enc_word);
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat("!ENCODED!WORD!" + e.getMessage() + "\n\n");
            return enc_word;
        }
    }

    /**
     * Convert an ASCII Quoted Printable to UTF-8 string.
     * Line length must be maximum 76 chars long.
     * Therefore a re-assembly is required, before processing.
     *
     * Creme de la creme:
     * Cr=C3=A8me de la cr=C3=A8me
     **/
    public static String parse_quoted_printable(String s, String charset) {
        try {
            // true applies 5 strict QP rules, false applies 2 QP rules
            QuotedPrintableCodec QPC = new QuotedPrintableCodec(Charset.forName(charset), false);

            // Reassembly
            String[] lines = s.split("\n");
            for (int i = 0; i < lines.length; i++) {// CR "=0D" LF "=0A"
                if (lines[i].endsWith("=")) {
                    lines[i] = lines[i].substring(0, lines[i].length() - 1);
                    continue;
                } else if (lines[i].endsWith("=\r")) {
                    lines[i] = lines[i].substring(0, lines[i].length() - 2);
                    continue;
                }
                lines[i] = lines[i] + "=0A";
            }
            return QPC.decode(TextUtils.join("", lines));
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            return s;
        }
    }

    static String parse_transfer_encoding(String transfer_enc, String data, String charset) {
        if (transfer_enc.matches("(?i).*BASE64.*")) {
            data = parse_BASE64(data);
        } else if (transfer_enc.matches("(?i).*QUOTED-PRINTABLE.*")) {
            data = parse_quoted_printable(data, charset);
        }
        return data;
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
                                .getBytes(), StandardCharsets.UTF_8);
                    }
                } else {
                    return filename;
                }
            } catch (UnsupportedEncodingException e) {
                InboxPager.log = InboxPager.log.concat("!!5:" + e.getMessage() + "\n\n");
                return filename;
            }
        }
    }

    static String to_ascii(String s) {
        return new String(s.getBytes(StandardCharsets.US_ASCII));
    }

    public static boolean all_ascii(String s) {
        for (char c: s.toCharArray()) { if (((int) c) > 127) return false; }
        return true;
    }

    public static String to_base64_utf8(String s) {
        return "=?utf-8?B?" + Base64.encodeToString(s.getBytes(), Base64.NO_WRAP) + "?=";
    }

    public static String s_file_size(long sz, String B, String KB, String MB) {
        if (sz == 0) {
            return "0 " + B;
        } else if (sz < 0) {
            return "? " + B;
        } else {
            if (sz < 1000) {
                return sz + " " + B;
            } else if (sz < 1000000) {
                return  (sz/1000) + " " + KB;
            } else {
                return (sz/1000000) + " " + MB;
            }
        }
    }

    /**
     * Approximate available storage space calculation.
     * Not very accurate.
     * Message/Attachment are assumed to be octets.
     * return: don't allow saving, if space is unknown.
     **/
    public static boolean capacity_exists(long file_size) {
        try {
            StatFs stat_fs = new StatFs("/storage");
            return stat_fs.getAvailableBytes() < file_size;
        } catch (Exception e) {
            InboxPager.log = InboxPager.log.concat(e.getMessage() + "\n\n");
            return true;
        }
    }

    // Counting address1@server.com, address2@server.com
    public static HashMap<String, String> parse_addresses(String[] addresses) {
        HashMap<String, String> addrs = new HashMap<>();

        int n = 0;
        for (String addressees : addresses) {
            ++n;
            if (addressees == null) continue;

            // Parse
            ArrayList<String> reflow_addr = new ArrayList<>();
            for (String blob : addressees.split(",")) {
                String adder = blob.trim().replaceAll("([\r\n])", "");

                if (!adder.isEmpty() && adder.contains("@")) {
                    // Address remains address@email.com
                    int ind;
                    ind = adder.indexOf("<");
                    if (ind > -1) adder = adder.substring(ind + 1, adder.indexOf(">"));

                    if (!reflow_addr.contains(adder)) reflow_addr.add(adder);
                }
            }

            // Serialize
            String serialized = "";
            for (int i = 0;i < reflow_addr.size();++i) {
                if (i == (reflow_addr.size() - 1)) {
                    serialized += reflow_addr.get(i);
                } else {
                    serialized = serialized.concat(reflow_addr.get(i) + ",");
                }
            }

            switch (n) {
                case 1:
                    addrs.put("TO_", reflow_addr.size() + "|" + serialized);
                    break;
                case 2:
                    addrs.put("CC_", reflow_addr.size() + "|" + serialized);
                    break;
                case 3:
                    addrs.put("BCC", reflow_addr.size() + "|" + serialized);
                    break;
            }
        }

        return addrs;
    }

    /**
     * Convert RFC 2822 to Date/Calendar.
     **/
    @SuppressLint("SimpleDateFormat")
    public static Date parse_date(String datetime) {
        boolean comma = datetime.contains(",");
        boolean cols = datetime.matches(".*:(\\d{2}):.*");

        SimpleDateFormat format;

        if (comma && cols) {
            format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
        } else if (comma) {
            format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm Z");
        } else if (cols) {
            format = new SimpleDateFormat("dd MMM yyyy HH:mm:ss Z");
        } else {
            format = new SimpleDateFormat("dd MMM yyyy HH:mm Z");
        }

        Date msg_date;
        try {
            msg_date = format.parse(datetime);
        } catch (ParseException pe) {
            InboxPager.log = InboxPager.log.concat(pe.getMessage() + "\n\n");
            return null;
        }

        return msg_date;
    }
}
