package com.stickersanimated.kissing.shop;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.stickersanimated.kissing.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shop's product description, laid out rather than dumped.
 *
 * <p>The shop writes these in HTML, and across the catalogue they are mostly bulleted
 * lists - over a thousand list items against four hundred paragraphs. {@link Html} has
 * no idea what a list is: it drops the markup and runs every item into one another, so
 * the part of the description doing the most work is the part that reads worst.
 *
 * <p>So the HTML is split into blocks here and each one becomes its own view: headings
 * in the shop's display face, paragraphs in its body face, and list items as a rose
 * bullet beside the text, which also gives the hanging indent a wrapped line needs.
 * Only the inline markup inside a block - bold, italic, entities - goes through
 * {@link Html}, which is the part it does well.
 */
public final class ShopDescription {

    /**
     * Paragraphs, headings and list items, in the order they appear. Anything else -
     * the ul and ol wrappers, the editor's data attributes - is passed over.
     */
    private static final Pattern BLOCK = Pattern.compile(
            "<(h[1-6]|p|li)[^>]*>(.*?)</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private ShopDescription() {
    }

    /**
     * Fills {@code container} with the description.
     *
     * @return false when there was nothing to show, so the caller can hide the section
     */
    public static boolean render(LinearLayout container, String html) {
        if (container == null || TextUtils.isEmpty(html)) {
            return false;
        }

        final List<Block> blocks = parse(html);
        if (blocks.isEmpty()) {
            return false;
        }

        final Context context = container.getContext();
        container.removeAllViews();
        boolean first = true;
        for (Block block : blocks) {
            container.addView(block.heading
                    ? heading(context, block, first)
                    : (block.bullet == null ? paragraph(context, block, first)
                                            : listItem(context, block)));
            first = false;
        }
        return true;
    }

    // ------------------------------------------------------------------ parsing

    private static List<Block> parse(String html) {
        final List<Block> blocks = new ArrayList<>();
        final Matcher matcher = BLOCK.matcher(html);

        int listStart = -1;
        int counter = 0;

        while (matcher.find()) {
            final String tag = matcher.group(1).toLowerCase(Locale.US);
            final CharSequence text = trim(fromHtml(matcher.group(2)));
            if (TextUtils.isEmpty(text)) {
                continue;
            }

            if ("li".equals(tag)) {
                // Which list this item belongs to, and whether it is numbered: the
                // nearest opening tag behind it decides both.
                final int ol = lastIndexOf(html, "<ol", matcher.start());
                final int ul = lastIndexOf(html, "<ul", matcher.start());
                final boolean ordered = ol > ul;
                final int start = Math.max(ol, ul);

                if (start != listStart) {
                    listStart = start;
                    counter = 0;
                }
                counter++;

                blocks.add(new Block(text, false, ordered ? counter + "." : "•"));
                continue;
            }

            // A paragraph or heading ends whatever list was running.
            listStart = -1;
            blocks.add(new Block(text, tag.charAt(0) == 'h', null));
        }
        return blocks;
    }

    private static int lastIndexOf(String haystack, String needle, int before) {
        return haystack.toLowerCase(Locale.US).lastIndexOf(needle, before);
    }

    @SuppressWarnings("deprecation")
    private static CharSequence fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT);
        }
        return Html.fromHtml(html);
    }

    /** Html always leaves trailing newlines behind; they would show as blank space. */
    private static CharSequence trim(CharSequence text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int start = 0;
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        return text.subSequence(start, end);
    }

    // ------------------------------------------------------------------ views

    private static TextView heading(Context context, Block block, boolean first) {
        final TextView view = new TextView(context);
        view.setText(block.text);
        view.setTypeface(ResourcesCompat.getFont(context, R.font.fraunces_bold));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        view.setTextColor(ContextCompat.getColor(context, R.color.shop_ink));
        view.setLayoutParams(margins(context, first ? 0 : 18, 6));
        return view;
    }

    private static TextView paragraph(Context context, Block block, boolean first) {
        final TextView view = new TextView(context);
        view.setText(block.text);
        view.setTypeface(ResourcesCompat.getFont(context, R.font.jakarta_regular));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTextColor(ContextCompat.getColor(context, R.color.shop_ink_2));
        view.setLineSpacing(dp(context, 5), 1f);
        view.setLayoutParams(margins(context, first ? 0 : 10, 0));
        return view;
    }

    /**
     * A bullet beside the text rather than in front of it, so the second line of a long
     * item lines up with the first instead of sliding back under the bullet.
     */
    private static LinearLayout listItem(Context context, Block block) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        row.setLayoutParams(margins(context, 7, 0));

        final TextView bullet = new TextView(context);
        bullet.setText(block.bullet);
        bullet.setTypeface(ResourcesCompat.getFont(context, R.font.jakarta_bold));
        bullet.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        bullet.setTextColor(ContextCompat.getColor(context, R.color.shop_rose));
        bullet.setLineSpacing(dp(context, 5), 1f);
        final LinearLayout.LayoutParams bulletParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bulletParams.rightMargin = (int) dp(context, 9);
        bullet.setLayoutParams(bulletParams);
        // Numbers are wider than a bullet, so pin a width and keep the text edges level.
        bullet.setMinWidth((int) dp(context, 14));

        final TextView text = new TextView(context);
        text.setText(block.text);
        text.setTypeface(ResourcesCompat.getFont(context, R.font.jakarta_regular));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        text.setTextColor(ContextCompat.getColor(context, R.color.shop_ink_2));
        text.setLineSpacing(dp(context, 5), 1f);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(bullet);
        row.addView(text);
        return row;
    }

    private static LinearLayout.LayoutParams margins(Context context, int top, int bottom) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = (int) dp(context, top);
        params.bottomMargin = (int) dp(context, bottom);
        return params;
    }

    private static float dp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    /** One paragraph, heading or list item. */
    private static final class Block {
        final CharSequence text;
        final boolean heading;
        /** The bullet or number to set beside it, or null when this is not a list item. */
        final String bullet;

        Block(CharSequence text, boolean heading, String bullet) {
            this.text = text;
            this.heading = heading;
            this.bullet = bullet;
        }
    }
}
