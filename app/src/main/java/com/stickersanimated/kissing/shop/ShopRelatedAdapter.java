package com.stickersanimated.kissing.shop;

import android.content.Context;
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

/** The row of other things from the same category, at the foot of a product. */
public class ShopRelatedAdapter extends RecyclerView.Adapter<ShopRelatedAdapter.Holder> {

    private final Context context;
    private final List<ShopProduct> products;
    private final ShopAdapter.OnProductTapped listener;

    public ShopRelatedAdapter(Context context, List<ShopProduct> products,
                              ShopAdapter.OnProductTapped listener) {
        this.context = context;
        this.products = products;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context)
                .inflate(R.layout.item_shop_related, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        final ShopProduct product = products.get(position);

        holder.name.setText(product.name == null ? "" : product.name);
        holder.price.setText(ShopPrice.format(product.price, product.currency));

        final String cover = product.cover();
        if (cover == null) {
            holder.image.setImageResource(R.drawable.sticker_error);
        } else {
            Picasso.get().load(cover).placeholder(R.drawable.sticker_error)
                    .error(R.drawable.sticker_error).fit().centerCrop().into(holder.image);
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

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.image_view_related);
            name = view.findViewById(R.id.text_view_related_name);
            price = view.findViewById(R.id.text_view_related_price);
        }
    }
}
