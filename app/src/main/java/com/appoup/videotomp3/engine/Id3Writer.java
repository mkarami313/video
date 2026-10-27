package com.appoup.videotomp3.engine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Prepends a minimal ID3v2.3 tag (title/artist/album/genre + optional cover)
 * to an existing MP3 file. Uses UTF-16 text frames and an APIC picture frame.
 */
public class Id3Writer {

    public static void apply(File mp3, String title, String artist, String album,
                             String genre, byte[] cover, String coverMime) throws Exception {
        boolean any = notEmpty(title) || notEmpty(artist) || notEmpty(album)
                || notEmpty(genre) || (cover != null && cover.length > 0);
        if (!any) return;

        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        if (notEmpty(title))  frames.write(textFrame("TIT2", title));
        if (notEmpty(artist)) frames.write(textFrame("TPE1", artist));
        if (notEmpty(album))  frames.write(textFrame("TALB", album));
        if (notEmpty(genre))  frames.write(textFrame("TCON", genre));
        if (cover != null && cover.length > 0) {
            frames.write(apicFrame(cover, coverMime == null ? "image/jpeg" : coverMime));
        }

        byte[] frameBytes = frames.toByteArray();
        byte[] header = new byte[10];
        header[0] = 'I'; header[1] = 'D'; header[2] = '3';
        header[3] = 3; header[4] = 0;    // v2.3.0
        header[5] = 0;                   // flags
        writeSynchSafe(header, 6, frameBytes.length);

        File tmp = new File(mp3.getParentFile(), mp3.getName() + ".id3tmp");
        OutputStream out = new FileOutputStream(tmp);
        out.write(header);
        out.write(frameBytes);
        FileInputStream in = new FileInputStream(mp3);
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
        in.close();
        out.flush();
        out.close();

        if (!mp3.delete()) throw new Exception("cannot replace original");
        if (!tmp.renameTo(mp3)) throw new Exception("cannot rename tagged file");
    }

    private static byte[] textFrame(String id, String value) throws Exception {
        // encoding byte 0x01 = UTF-16 with BOM
        byte[] text = value.getBytes("UTF-16");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(1);
        body.write(text);
        return frame(id, body.toByteArray());
    }

    private static byte[] apicFrame(byte[] image, String mime) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);                              // text encoding ISO-8859-1
        body.write(mime.getBytes("ISO-8859-1"));
        body.write(0);                              // mime terminator
        body.write(3);                              // picture type: front cover
        body.write(0);                              // empty description terminator
        body.write(image);
        return frame("APIC", body.toByteArray());
    }

    private static byte[] frame(String id, byte[] body) throws Exception {
        ByteArrayOutputStream f = new ByteArrayOutputStream();
        f.write(id.getBytes("ISO-8859-1"));
        byte[] size = new byte[4];
        int len = body.length;
        size[0] = (byte) ((len >> 24) & 0xff);
        size[1] = (byte) ((len >> 16) & 0xff);
        size[2] = (byte) ((len >> 8) & 0xff);
        size[3] = (byte) (len & 0xff);
        f.write(size);
        f.write(0); f.write(0);                     // flags
        f.write(body);
        return f.toByteArray();
    }

    private static void writeSynchSafe(byte[] b, int off, int size) {
        b[off] = (byte) ((size >> 21) & 0x7f);
        b[off + 1] = (byte) ((size >> 14) & 0x7f);
        b[off + 2] = (byte) ((size >> 7) & 0x7f);
        b[off + 3] = (byte) (size & 0x7f);
    }

    private static boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
