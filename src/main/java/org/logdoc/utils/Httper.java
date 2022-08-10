package org.logdoc.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import static org.logdoc.utils.Tools.isEmpty;
import static org.logdoc.utils.Tools.notNull;


public class Httper {
    private final Map<String, String> headers;

    public Httper() {
        headers = new HashMap<>(0);
    }

    public void addHeader(final String name, final String value) {
        if (!isEmpty(name) && !isEmpty(value))
            headers.put(name, value);
    }

    public Action exec(final URL url, final String method, final long timeOutMs, final Consumer<OutputStream> feeder, final boolean readAnswer) throws Exception {
        final ByteArrayOutputStream bosRsp = new ByteArrayOutputStream(16);
        int code;

        final URLConnection cn = url.openConnection();

        cn.setDoInput(true);
        cn.setDoOutput(true);

        ((HttpURLConnection) cn).setRequestMethod(method);
        ((HttpURLConnection) cn).setInstanceFollowRedirects(false);
        cn.setUseCaches(false);

        if (timeOutMs > 0) {
            cn.setConnectTimeout((int) timeOutMs);
            cn.setReadTimeout((int) timeOutMs);
        }

        for (final String headerName : this.headers.keySet()) {
            cn.setRequestProperty(headerName, this.headers.get(headerName));
        }

        for (final String headerName : headers.keySet()) {
            cn.setRequestProperty(headerName, headers.get(headerName));
        }

        cn.connect();

        if (feeder != null)
            feeder.accept(cn.getOutputStream());

        code = ((HttpURLConnection) cn).getResponseCode();

        if (readAnswer) {
            InputStream is = code >= 400 ? ((HttpURLConnection) cn).getErrorStream() : cn.getInputStream();

            switch (notNull(cn.getContentEncoding())) {
                case "gzip":
                    is = new GZIPInputStream(is);
                    break;
                case "deflate":
                    is = new InflaterInputStream(is);
                    break;
            }

            int read;
            while ((read = is.read()) != -1)
                bosRsp.write(read);

            try {is.close();} catch (final Exception ignore) {}
        }

        return new Action(code, readAnswer ? new String(bosRsp.toByteArray(), StandardCharsets.UTF_8) : "", bosRsp.toByteArray());
    }

    public static class Action {
        public final int responseCode;
        public final String responseMessage;
        public final byte[] response;

        private Action(final int responseCode, final String responseMessage, final byte[] response) {
            this.responseCode = responseCode;
            this.responseMessage = responseMessage;
            this.response = response;
        }
    }
}
