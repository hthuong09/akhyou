package dulleh.akhyou.Search.Holder.Item;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;
import dulleh.akhyou.Models.Anime;
import dulleh.akhyou.Models.Providers;
import dulleh.akhyou.Models.SearchProviders.BamSearchProvider;
import dulleh.akhyou.Models.SearchProviders.KissSearchProvider;
import dulleh.akhyou.Models.SearchProviders.RamSearchProvider;
import dulleh.akhyou.Models.SearchProviders.RushSearchProvider;
import dulleh.akhyou.Models.SearchProviders.SearchProvider;
import dulleh.akhyou.R;
import dulleh.akhyou.Search.Holder.SearchHolderAdapter;
import dulleh.akhyou.Search.Holder.SearchHolderFragment;
import dulleh.akhyou.Utils.CloudFlareInitializationException;
import dulleh.akhyou.Utils.CloudflareHttpClient;
import dulleh.akhyou.Utils.Events.SearchEvent;
import dulleh.akhyou.Utils.Events.SnackbarEvent;
import dulleh.akhyou.Utils.GeneralUtils;
import nucleus.presenter.RxPresenter;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

public class SearchPresenter extends RxPresenter<SearchFragment> {
    private static Throwable CLOUDFLARETHROWABLE = new Throwable("Wait 5 seconds and try again (or don't).");
    public int providerType;

    private Subscription subscription;
    private SearchProvider searchProvider;

    private String searchTerm;
    public boolean isRefreshing;

    // call in fragment onCreate()
    public void setProviderType (int providerType) {
        this.providerType = providerType;
        switch (providerType) {
            case Providers.RUSH:
                searchProvider = new RushSearchProvider();
                break;
            case Providers.RAM:
                searchProvider = new RamSearchProvider();
                break;
            case Providers.BAM:
                searchProvider = new BamSearchProvider();
                break;
            case Providers.KISS:
                searchProvider = new KissSearchProvider();
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            setProviderType(savedState.getInt(SearchHolderAdapter.PROVIDER_TYPE_KEY, 0));
        }
    }

    @Override
    protected void onSave(Bundle state) {
        super.onSave(state);
        state.putInt(SearchHolderAdapter.PROVIDER_TYPE_KEY, providerType);
    }

    @Override
    protected void onTakeView(SearchFragment view) {
        super.onTakeView(view);
        subscribe();
        view.updateRefreshing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchProvider = null;
        unsubscribe();
    }

    private void subscribe () {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().registerSticky(this);
        }
    }

    private void unsubscribe () {
        if (subscription != null && !subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
        EventBus.getDefault().unregister(this);
    }

    public void onEvent (SearchEvent event) {
        this.searchTerm = event.searchTerm;
        search();
    }

    public void search () {
        isRefreshing = true;

        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            isRefreshing = false;
            SearchHolderFragment.searchResultsCache.set(providerType, new ArrayList<>(0));
            getView().updateSearchResults();

            postError(new Throwable("Please enter a search term."));

        } else {

            if (getView() != null && !getView().isRefreshing()) {
                getView().updateRefreshing();
            }

            if (subscription != null && !subscription.isUnsubscribed()) {
                subscription.unsubscribe();
            }

            subscription = Observable.defer(new Func0<Observable<List<Anime>>>() {
                @Override
                public Observable<List<Anime>> call() {
                    try {
                        return Observable.just(searchProvider.searchFor(searchTerm));
                    } catch (CloudFlareInitializationException cf) {
                        CloudflareHttpClient.INSTANCE.registerSites();
                        return Observable.error(CLOUDFLARETHROWABLE);
                    }
                }
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .compose(this.deliver())
                    .subscribe(new Subscriber<List<Anime>>() {
                        @Override
                        public void onNext(List<Anime> animes) {
                            SearchHolderFragment.searchResultsCache.set(providerType, animes);
                            isRefreshing = false;
                            getView().updateSearchResults();
                            this.unsubscribe();
                        }

                        @Override
                        public void onCompleted() {
                            // should be using Observable.just() as onCompleted is never called
                            // and it only runs once.
                        }

                        @Override
                        public void onError(Throwable e) {
                            isRefreshing = false;
                            SearchHolderFragment.searchResultsCache.set(providerType, new ArrayList<>(0));
                            getView().updateSearchResults();

                            postError(e);

                            this.unsubscribe();
                        }
                    });
        }
    }

    public void postError (Throwable e) {
        e.printStackTrace();

        View.OnClickListener actionOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search();
            }
        };

        EventBus.getDefault().post(new SnackbarEvent(
                GeneralUtils.formatError(e),
                Snackbar.LENGTH_LONG,
                "Retry",
                actionOnClick,
                getView().getResources().getColor(R.color.accent)
        ));

    }

}
