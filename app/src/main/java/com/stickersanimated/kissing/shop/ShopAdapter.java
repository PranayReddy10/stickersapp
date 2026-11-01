package com.stickersanimated.kissing.shop;

import android.content.Context;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.stickersanimated.kissing.R;

import java.util.List;

/** The product grid. */
public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.Holder> {

    public interface OnProductTapped {
        void onProductTapped(ShopProduct product);
    }

    private final Context context;
    private final List<ShopProduct> products;
    private final OnProductTapped listener;

    public ShopAdapter(Context context, List<ShopProduct> products, OnProductTapped listener) {
        this.context = context;
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context)
                .inflate(R.layout.item_shop_product, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final ShopProduct product = products.get(position);

        holder.name.setText(product.name == null ? "" : product.name);
        holder.price.setText(ShopPrice.format(product.price, product.currency));

        if (product.discounted()) {
            holder.wasPrice.setVisibility(View.VISIBLE);
            holder.wasPrice.setText(ShopPrice.format(product.regularPrice, product.currency));
            // Set every time: a recycled holder may still be carrying the flag.
            holder.wasPrice.setPaintFlags(
                    holder.wasPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            holder.wasPrice.setVisibility(View.GONE);
        }

        if (product.discounted() && product.discountPercent > 0) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(context.getString(R.string.shop_discount, product.discountPercent));
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        holder.soldOut.setVisibility(product.inStock ? View.GONE : View.VISIBLE);
        holder.image.setAlpha(product.inStock ? 1f : 0.45f);

        final String cover = product.cover();
        if (cover == null) {
            holder.image.setImageResource(R.drawable.sticker_error);
        } else {
            Picasso.get().load(cover).placeholder(R.drawable.sticker_error)
                    .error(R.drawable.sticker_error).fit().centerInside().into(holder.image);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (listener != null) {
                    listener.onProductTapped(product);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView price;
        final TextView wasPrice;
        final TextView badge;
        final TextView soldOut;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.image_view_shop_product);
            name = view.findViewById(R.id.text_view_shop_name);
            price = view.findViewById(R.id.text_view_shop_price);
            wasPrice = view.findViewById(R.id.text_view_shop_was_price);
            badge = view.findViewById(R.id.text_view_shop_badge);
            soldOut = view.findViewById(R.id.text_view_shop_sold_out);
        }
    }
}
