package com.fnostv.android4.ui;

import android.graphics.Path;

/**
 * 极简 SVG path 解析器（M/L/H/V/C/S/Q/T/A/Z 及相对指令）。
 *
 * <p>网页侧栏图标都是 24×24 的 SVG fill path，直接拿 {@code d} 字符串在 Canvas 上还原，
 * 比手写近似图形更准确。Android 4.4 没有 VectorDrawable，所以这里自己解析成 {@link Path}。
 */
final class SvgPath {
    private SvgPath() {
    }

    static void parse(String data, Path path) {
        if (data == null || data.length() == 0) {
            return;
        }
        new Parser(data, path).run();
    }

    private static final class Parser {
        private final String data;
        private final Path path;
        private int index;
        private float cx;
        private float cy;
        private float startX;
        private float startY;
        private float lastControlX;
        private float lastControlY;
        private char lastCommand;

        Parser(String data, Path path) {
            this.data = data;
            this.path = path;
            this.path.reset();
        }

        void run() {
            while (true) {
                skipSeparators();
                if (index >= data.length()) {
                    return;
                }
                char cmd = data.charAt(index);
                if (Character.isLetter(cmd)) {
                    index++;
                } else {
                    cmd = lastCommand;
                    if (cmd == 0) {
                        index++;
                        continue;
                    }
                }
                char upper = Character.toUpperCase(cmd);
                boolean relative = Character.isLowerCase(cmd);
                switch (upper) {
                    case 'M': {
                        float x = number();
                        float y = number();
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        cx = x;
                        cy = y;
                        startX = x;
                        startY = y;
                        path.moveTo(x, y);
                        lastCommand = relative ? 'l' : 'L';
                        break;
                    }
                    case 'L': {
                        float x = number();
                        float y = number();
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        cx = x;
                        cy = y;
                        path.lineTo(x, y);
                        lastCommand = cmd;
                        break;
                    }
                    case 'H': {
                        float x = number();
                        if (relative) {
                            x += cx;
                        }
                        cx = x;
                        path.lineTo(x, cy);
                        lastCommand = cmd;
                        break;
                    }
                    case 'V': {
                        float y = number();
                        if (relative) {
                            y += cy;
                        }
                        cy = y;
                        path.lineTo(cx, y);
                        lastCommand = cmd;
                        break;
                    }
                    case 'C': {
                        float x1 = number();
                        float y1 = number();
                        float x2 = number();
                        float y2 = number();
                        float x = number();
                        float y = number();
                        if (relative) {
                            x1 += cx;
                            y1 += cy;
                            x2 += cx;
                            y2 += cy;
                            x += cx;
                            y += cy;
                        }
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        lastControlX = x2;
                        lastControlY = y2;
                        cx = x;
                        cy = y;
                        lastCommand = cmd;
                        break;
                    }
                    case 'S': {
                        float x2 = number();
                        float y2 = number();
                        float x = number();
                        float y = number();
                        if (relative) {
                            x2 += cx;
                            y2 += cy;
                            x += cx;
                            y += cy;
                        }
                        float x1 = 2 * cx - lastControlX;
                        float y1 = 2 * cy - lastControlY;
                        path.cubicTo(x1, y1, x2, y2, x, y);
                        lastControlX = x2;
                        lastControlY = y2;
                        cx = x;
                        cy = y;
                        lastCommand = cmd;
                        break;
                    }
                    case 'Q': {
                        float x1 = number();
                        float y1 = number();
                        float x = number();
                        float y = number();
                        if (relative) {
                            x1 += cx;
                            y1 += cy;
                            x += cx;
                            y += cy;
                        }
                        path.quadTo(x1, y1, x, y);
                        lastControlX = x1;
                        lastControlY = y1;
                        cx = x;
                        cy = y;
                        lastCommand = cmd;
                        break;
                    }
                    case 'T': {
                        float x = number();
                        float y = number();
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        float x1 = 2 * cx - lastControlX;
                        float y1 = 2 * cy - lastControlY;
                        path.quadTo(x1, y1, x, y);
                        lastControlX = x1;
                        lastControlY = y1;
                        cx = x;
                        cy = y;
                        lastCommand = cmd;
                        break;
                    }
                    case 'A': {
                        float rx = number();
                        float ry = number();
                        float rotation = number();
                        // 大弧/扫描标志在压缩的 SVG 里可能连写（如 110），必须逐位读
                        boolean largeArc = flag();
                        boolean sweep = flag();
                        float x = number();
                        float y = number();
                        if (relative) {
                            x += cx;
                            y += cy;
                        }
                        arcTo(rx, ry, rotation, largeArc, sweep, x, y);
                        lastCommand = cmd;
                        break;
                    }
                    case 'Z': {
                        path.close();
                        cx = startX;
                        cy = startY;
                        lastCommand = cmd;
                        break;
                    }
                    default:
                        index++;
                        break;
                }
            }
        }

        private void skipSeparators() {
            while (index < data.length()) {
                char c = data.charAt(index);
                if (c == ',' || Character.isWhitespace(c)) {
                    index++;
                } else {
                    return;
                }
            }
        }

        /** 读取一个 SVG 数字（隐式分隔：第二个小数点、正负号、命令字母都会断开）。 */
        private float number() {
            skipSeparators();
            int length = data.length();
            int begin = index;
            boolean seenDigit = false;
            boolean seenDot = false;
            if (index < length && (data.charAt(index) == '-' || data.charAt(index) == '+')) {
                index++;
            }
            while (index < length) {
                char c = data.charAt(index);
                if (c >= '0' && c <= '9') {
                    seenDigit = true;
                    index++;
                } else if (c == '.') {
                    if (seenDot) {
                        break;
                    }
                    seenDot = true;
                    index++;
                } else if ((c == 'e' || c == 'E') && seenDigit) {
                    index++;
                    if (index < length && (data.charAt(index) == '-' || data.charAt(index) == '+')) {
                        index++;
                    }
                    while (index < length && Character.isDigit(data.charAt(index))) {
                        index++;
                    }
                    break;
                } else {
                    break;
                }
            }
            if (!seenDigit) {
                if (index == begin && index < length) {
                    index++;
                }
                return 0f;
            }
            try {
                return Float.parseFloat(data.substring(begin, index));
            } catch (NumberFormatException ex) {
                return 0f;
            }
        }

        /** 弧标志位：单个 0/1。 */
        private boolean flag() {
            skipSeparators();
            if (index < data.length()) {
                char c = data.charAt(index);
                if (c == '0' || c == '1') {
                    index++;
                    return c == '1';
                }
            }
            return false;
        }

        /** SVG 椭圆弧 → 三次贝塞尔（端点参数化）。 */
        private void arcTo(float rx, float ry, float rotationDeg, boolean largeArc,
                           boolean sweep, float x, float y) {
            if (rx == 0f || ry == 0f) {
                path.lineTo(x, y);
                cx = x;
                cy = y;
                return;
            }
            rx = Math.abs(rx);
            ry = Math.abs(ry);
            double phi = Math.toRadians(rotationDeg % 360.0);
            double cosPhi = Math.cos(phi);
            double sinPhi = Math.sin(phi);

            double dx2 = (cx - x) / 2.0;
            double dy2 = (cy - y) / 2.0;
            double x1p = cosPhi * dx2 + sinPhi * dy2;
            double y1p = -sinPhi * dx2 + cosPhi * dy2;

            double rx2 = rx * rx;
            double ry2 = ry * ry;
            double x1p2 = x1p * x1p;
            double y1p2 = y1p * y1p;
            double lambda = x1p2 / rx2 + y1p2 / ry2;
            if (lambda > 1) {
                double scale = Math.sqrt(lambda);
                rx *= scale;
                ry *= scale;
                rx2 = rx * rx;
                ry2 = ry * ry;
            }

            double sign = largeArc != sweep ? 1.0 : -1.0;
            double numerator = rx2 * ry2 - rx2 * y1p2 - ry2 * x1p2;
            double denominator = rx2 * y1p2 + ry2 * x1p2;
            double coefficient = denominator == 0 ? 0
                    : sign * Math.sqrt(Math.max(0, numerator / denominator));
            double cxp = coefficient * (rx * y1p / ry);
            double cyp = coefficient * (-ry * x1p / rx);

            double centerX = cosPhi * cxp - sinPhi * cyp + (cx + x) / 2.0;
            double centerY = sinPhi * cxp + cosPhi * cyp + (cy + y) / 2.0;

            double theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry);
            double delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry,
                    (-x1p - cxp) / rx, (-y1p - cyp) / ry);
            if (!sweep && delta > 0) {
                delta -= 2 * Math.PI;
            } else if (sweep && delta < 0) {
                delta += 2 * Math.PI;
            }

            int segments = Math.max(1, (int) Math.ceil(Math.abs(delta) / (Math.PI / 2)));
            double step = delta / segments;
            double handle = 4.0 / 3.0 * Math.tan(step / 4.0);
            double theta = theta1;
            for (int i = 0; i < segments; i++) {
                double cos1 = Math.cos(theta);
                double sin1 = Math.sin(theta);
                double theta2 = theta + step;
                double cos2 = Math.cos(theta2);
                double sin2 = Math.sin(theta2);

                double p1x = centerX + cosPhi * rx * cos1 - sinPhi * ry * sin1;
                double p1y = centerY + sinPhi * rx * cos1 + cosPhi * ry * sin1;
                double p2x = centerX + cosPhi * rx * cos2 - sinPhi * ry * sin2;
                double p2y = centerY + sinPhi * rx * cos2 + cosPhi * ry * sin2;

                double d1x = -cosPhi * rx * sin1 - sinPhi * ry * cos1;
                double d1y = -sinPhi * rx * sin1 + cosPhi * ry * cos1;
                double d2x = -cosPhi * rx * sin2 - sinPhi * ry * cos2;
                double d2y = -sinPhi * rx * sin2 + cosPhi * ry * cos2;

                path.cubicTo((float) (p1x + handle * d1x), (float) (p1y + handle * d1y),
                        (float) (p2x - handle * d2x), (float) (p2y - handle * d2y),
                        (float) p2x, (float) p2y);
                theta = theta2;
            }
            cx = x;
            cy = y;
        }

        private double angle(double ux, double uy, double vx, double vy) {
            double dot = ux * vx + uy * vy;
            double length = Math.sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy));
            if (length == 0) {
                return 0;
            }
            double value = Math.acos(Math.max(-1, Math.min(1, dot / length)));
            return (ux * vy - uy * vx) < 0 ? -value : value;
        }
    }
}
