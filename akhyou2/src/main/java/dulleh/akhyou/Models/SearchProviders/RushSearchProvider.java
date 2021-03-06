package dulleh.akhyou.Models.SearchProviders;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

import dulleh.akhyou.Models.Anime;
import dulleh.akhyou.Models.Providers;
import dulleh.akhyou.Utils.CloudFlareInitializationException;
import dulleh.akhyou.Utils.CloudflareHttpClient;
import dulleh.akhyou.Utils.GeneralUtils;
import rx.exceptions.OnErrorThrowable;

public class RushSearchProvider implements SearchProvider{

    @Override
    public List<Anime> searchFor(String searchTerm) throws CloudFlareInitializationException {

        if (!CloudflareHttpClient.INSTANCE.isInitialized()) {
            throw new CloudFlareInitializationException();
        }

        String url = Providers.RUSH_SEARCH_URL + GeneralUtils.encodeForUtf8(searchTerm);

        String responseBody = GeneralUtils.getWebPage(url);

        Element searchResultsBox =  isolate(responseBody);

        if (!hasSearchResults(searchResultsBox)) {
            throw OnErrorThrowable.from(new Throwable("No search results."));
        }

        Elements searchResults = seperateResults(searchResultsBox);

        return parseResults(searchResults);
    }

    @Override
    public Element isolate (String document) {
        return Jsoup
                .parse(document, "http://www.animerush.tv/")
                .select("div#left-column > div.amin_box2 > div.amin_box_mid")
                .first();
    }

    @Override
    public boolean hasSearchResults(Element element) throws OnErrorThrowable {
        if (element.select("div.success").isEmpty()) {
            return false;
        }
        return true;
    }

    private Elements seperateResults (Element searchResultsBox) {
        return searchResultsBox.select("div.search-page_in_box_mid_link");
    }

    private List<Anime> parseResults (Elements searchResults) {
        List<Anime> animes = new ArrayList<>(searchResults.size());
        for (Element searchResult : searchResults) {
            Anime anime = new Anime().setProviderType(Providers.RUSH);

            anime.setTitle(searchResult.select("h3").text().trim()
            /*
            * Temporary fix for bug with AnimeRush search
            * can reproduce bug by including spaces in between letters in your search
            * E.G. "de tective co   nan"
            */
                    .replaceAll("<b>", "").replaceAll("</b>", ""));

            anime.setUrl(searchResult.select("a.highlightit").attr("href"))
                    .setDesc(searchResult.select("p").text())
                    .setImageUrl(searchResult.select("object.highlightz").attr("data"));

            animes.add(anime);
        }
        return animes;
    }

}
