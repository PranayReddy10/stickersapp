package com.stickersanimated.kissing.shop;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.squareup.picasso.Picasso;
import com.stickersanimated.kissing.R;

import java.util.List;

/** The swipeable pictures at the top of a product. */
public class ShopImagesAdapter extends RecyclerView.Adapter<ShopImagesAdapter.Holder> {

    private final Context context;
    private final List<String> urls;

    public ShopImagesAdapter(Context context, List<String> urls) {
        this.context = context;
        this.urls = urls;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(context)
                .inflate(R.layout.item_shop_image, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Picasso.get().load(urls.get(position))
                .placeholder(R.drawable.sticker_error)
                .error(R.drawable.sticker_error)
                .fit().centerInside()
                .into(holder.image);
    }

    @Override
    public int getItemCount() {
        return urls.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.image_view_shop_page);
        }
    }
}
