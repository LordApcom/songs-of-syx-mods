package lordapcom.bridge;

import java.util.Map;

/** Minimal JSON writer: null, Boolean, Number, Map, Iterable, Object[] and anything else as string. */
final class Json {

	static String write(Object o) {
		StringBuilder sb = new StringBuilder();
		write(sb, o);
		return sb.toString();
	}

	private static void write(StringBuilder sb, Object o) {
		if (o == null) {
			sb.append("null");
		} else if (o instanceof Boolean) {
			sb.append(o);
		} else if (o instanceof Number) {
			double d = ((Number) o).doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d))
				sb.append("null");
			else
				sb.append(o);
		} else if (o instanceof Map) {
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
				if (!first)
					sb.append(',');
				first = false;
				str(sb, String.valueOf(e.getKey()));
				sb.append(':');
				write(sb, e.getValue());
			}
			sb.append('}');
		} else if (o instanceof Iterable) {
			sb.append('[');
			boolean first = true;
			for (Object v : (Iterable<?>) o) {
				if (!first)
					sb.append(',');
				first = false;
				write(sb, v);
			}
			sb.append(']');
		} else if (o instanceof Object[]) {
			sb.append('[');
			Object[] a = (Object[]) o;
			for (int i = 0; i < a.length; i++) {
				if (i > 0)
					sb.append(',');
				write(sb, a[i]);
			}
			sb.append(']');
		} else {
			str(sb, o.toString());
		}
	}

	private static void str(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '"': sb.append("\\\""); break;
			case '\\': sb.append("\\\\"); break;
			case '\n': sb.append("\\n"); break;
			case '\r': sb.append("\\r"); break;
			case '\t': sb.append("\\t"); break;
			default:
				if (c < 0x20)
					sb.append(String.format("\\u%04x", (int) c));
				else
					sb.append(c);
			}
		}
		sb.append('"');
	}
}
