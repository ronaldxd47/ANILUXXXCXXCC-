/**
 * Samehadaku Dynamic Proxy Scraper Server
 * Implements HTML Parsing & AJAX Reverse Engineering
 * For modern, offline-resilient Native Mobile Client integration.
 * 
 * Recommended Deployment: Vercel, Render, Heroku, or Private VPS.
 */

const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');

const app = express();
const PORT = process.env.PORT || 3000;

// Enable cross-origin resource sharing for native and web accessibility
app.use(cors());
app.use(express.json());

// List of available active domain mirrors to handle automatic failover/redirect bypass
const DOMAINS = [
  "https://samehadaku.li",
  "https://samehadaku.care",
  "https://v2.samehadaku.how"
];

let ACTIVE_BASE_URL = DOMAINS[0];

const USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

/**
 * Normalizes URLs to use the current dynamically active fallback domain
 */
function normalizeUrl(originalUrl, targetDomain) {
  if (!originalUrl) return "";
  let url = originalUrl;
  const hosts = ["v2.samehadaku.how", "samehadaku.care", "samehadaku.li", "samehadaku.co", "samehadaku.ch", "samehadaku.vip"];
  const domainHost = targetDomain.replace("https://", "").replace("http://", "").split('/')[0];
  
  for (const host of hosts) {
    if (host !== domainHost && url.includes(host)) {
      url = url.replace(host, domainHost);
    }
  }
  return url;
}

/**
 * Fetch HTML Helper with automatic domain rotation & fallback handling
 */
async function fetchDocument(urlPathProvider) {
  let lastError = null;

  for (const domain of DOMAINS) {
    const targetUrl = urlPathProvider(domain);
    try {
      console.log(`[Proxy Scraper] Attempting to fetch: ${targetUrl}`);
      const response = await axios.get(targetUrl, {
        headers: {
          'User-Agent': USER_AGENT,
          'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
          'Accept-Language': 'id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7',
          'Upgrade-Insecure-Requests': '1'
        },
        timeout: 10000
      });

      // Update the active working url if fallback succeeds
      if (ACTIVE_BASE_URL !== domain) {
        ACTIVE_BASE_URL = domain;
        console.log(`[Proxy Scraper] Active mirror updated to: ${ACTIVE_BASE_URL}`);
      }

      return { html: response.data, finalDomain: domain, finalUrl: targetUrl };
    } catch (error) {
      console.warn(`[Proxy Scraper] Failed mirror fetch on ${targetUrl}: ${error.message}`);
      lastError = error;
    }
  }

  throw new Error(`Fallback failed for all domains. Last error: ${lastError ? lastError.message : 'Unknown error'}`);
}

/**
 * Helper to turn relative paths or corrupted image sources into stable URLs
 */
function cleanImageSrc(src, currentDomain) {
  if (!src) return "";
  let clean = src.trim();
  if (clean.startsWith("//")) {
    clean = "https:" + clean;
  } else if (clean.startsWith("/")) {
    clean = currentDomain + clean;
  }
  return clean;
}

/**
 * 1. GET Latest Uploads Feed
 * e.g., GET /api/latest?page=1
 */
app.get('/api/latest', async (req, res) => {
  try {
    const page = parseInt(req.query.page) || 1;
    
    const { html, finalDomain } = await fetchDocument((domain) => {
      return page > 1 ? `${domain}/page/${page}/` : domain;
    });

    const $ = cheerio.load(html);
    const results = [];

    // Parse the typical grid structure (e.g., '.post-show' or 'div.animepost' or '.hentry')
    const postElements = $(".post-show ul li, div.animepost, .hentry");
    
    postElements.each((index, element) => {
      const el = $(element);
      const title = el.select(".title, h2, h3.title").first().text().trim() || el.find(".title, h2").text().trim();
      const rawLink = el.find("a").first().attr("href") || "";
      const link = normalizeUrl(rawLink, finalDomain);
      
      let imageUrl = el.find("img").first().attr("src") || el.find("img").first().attr("data-src") || "";
      imageUrl = cleanImageSrc(imageUrl, finalDomain);

      // Metadatas
      const status = el.find(".type, .status, .ep").first().text().trim() || "Ongoing";
      const episode = el.find(".ep, .episode").first().text().trim() || "N/A";
      const score = el.find(".score, .rating").first().text().trim() || "Rating N/A";
      const type = el.find(".type, .format").first().text().trim() || "TV";

      if (title && link) {
        results.push({ title, link, imageUrl, status, episode, score, type });
      }
    });

    res.json({
      success: true,
      page,
      domain: finalDomain,
      results
    });
  } catch (error) {
    console.error(`[Proxy API] Error inside /api/latest:`, error);
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * 2. GET Search Results
 * e.g., GET /api/search?q=solo%20leveling&page=1
 */
app.get('/api/search', async (req, res) => {
  try {
    const query = req.query.q || '';
    const page = parseInt(req.query.page) || 1;

    if (!query) {
      return res.status(400).json({ success: false, error: "Query parameter 'q' is mandatory." });
    }

    const { html, finalDomain } = await fetchDocument((domain) => {
      const q = encodeURIComponent(query);
      return page > 1 ? `${domain}/page/${page}/?s=${q}` : `${domain}/?s=${q}`;
    });

    const $ = cheerio.load(html);
    const results = [];

    const postElements = $(".post-show ul li, div.animepost, .hentry");
    
    postElements.each((index, element) => {
      const el = $(element);
      const title = el.find(".title, h2, h3.title").first().text().trim();
      const rawLink = el.find("a").first().attr("href") || "";
      const link = normalizeUrl(rawLink, finalDomain);
      
      let imageUrl = el.find("img").first().attr("src") || el.find("img").first().attr("data-src") || "";
      imageUrl = cleanImageSrc(imageUrl, finalDomain);

      const status = el.find(".type, .status, .ep").first().text().trim() || "Completed";
      const episode = el.find(".ep, .episode").first().text().trim() || "Full";
      const score = el.find(".score, .rating").first().text().trim() || "N/A";
      const type = el.find(".type, .format").first().text().trim() || "TV";

      if (title && link) {
        results.push({ title, link, imageUrl, status, episode, score, type });
      }
    });

    res.json({
      success: true,
      query,
      page,
      domain: finalDomain,
      results
    });
  } catch (error) {
    console.error(`[Proxy API] Error inside /api/search:`, error);
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * 3. GET Anime Details (Information Page)
 * e.g., GET /api/anime?url=https://v2.samehadaku.how/anime/solo-leveling-s2/
 */
app.get('/api/anime', async (req, res) => {
  try {
    const animeUrl = req.query.url;

    if (!animeUrl) {
      return res.status(400).json({ success: false, error: "Query parameter 'url' is mandatory." });
    }

    const { html, finalDomain } = await fetchDocument((domain) => {
      return normalizeUrl(animeUrl, domain);
    });

    const $ = cheerio.load(html);

    // Header Metadata parsing
    const title = $(".info-anime h1, .entry-title, .anime-title").first().text().trim();
    let posterUrl = $(".info-anime .thumb img, .poster img").first().attr("src") || $(".info-anime .thumb img").first().attr("data-src") || "";
    posterUrl = cleanImageSrc(posterUrl, finalDomain);

    const synopsis = $(".entry-content-single, .desc, .sinopsis, [itemprop=description]").first().text().replace(/Samehadaku/gi, "").trim();
    const rating = $(".rating-value, .score, .rating").first().text().trim() || "Unknown";

    // Dynamic metadata tables mapping
    let status = "Ongoing";
    let type = "TV";
    let studio = "Unknown";
    let released = "Unknown";
    let ratingAge = "13+";
    const genres = [];

    $(".info-content .spe span, .info-anime span, .anime-metadata li").each((i, el) => {
      const text = $(el).text();
      if (text.includes("Status:")) status = text.replace("Status:", "").trim();
      if (text.includes("Type:")) type = text.replace("Type:", "").trim();
      if (text.includes("Studio:")) studio = text.replace("Studio:", "").trim();
      if (text.includes("Released:") || text.includes("Rilis:")) released = text.replace(/Released:|Rilis:/, "").trim();
      if (text.includes("Rating:")) ratingAge = text.replace("Rating:", "").trim();
    });

    // Genres parsing
    $(".genre-info a, .genres-container a, .gnr a").each((i, el) => {
      const genre = $(el).text().trim();
      if (genre) genres.push(genre);
    });

    // Episode list parsing
    const episodes = [];
    $(".lstepsiode ul li, .listeps ul li, .episode-list li").each((i, el) => {
      const li = $(el);
      const epTitle = li.find(".eps a, .entry-title a, a").first().text().trim();
      const rawEpLink = li.find("a").first().attr("href") || "";
      const epLink = normalizeUrl(rawEpLink, finalDomain);
      const epDate = li.find(".date").text().trim();

      if (epTitle && epLink) {
        episodes.push({
          title: epTitle,
          link: epLink,
          date: epDate || "Recently released"
        });
      }
    });

    res.json({
      success: true,
      domain: finalDomain,
      data: {
        title,
        posterUrl,
        synopsis,
        rating,
        status,
        type,
        studio,
        released,
        ratingAge,
        genres,
        episodes
      }
    });
  } catch (error) {
    console.error(`[Proxy API] Error inside /api/anime:`, error);
    res.status(500).json({ success: false, error: error.message });
  }
});

/**
 * 4. GET Episode Details & Decrypt Video Players (CRITICAL IMPLEMENTATION)
 * e.g., GET /api/episode?url=https://v2.samehadaku.how/one-piece-episode-1145/
 */
app.get('/api/episode', async (req, res) => {
  try {
    const episodeUrl = req.query.url;

    if (!episodeUrl) {
      return res.status(400).json({ success: false, error: "Query parameter 'url' is mandatory." });
    }

    // Phase 1: Retrieve the primary Episode Page structure
    const { html, finalDomain, finalUrl } = await fetchDocument((domain) => {
      return normalizeUrl(episodeUrl, domain);
    });

    const $ = cheerio.load(html);
    const title = $(".entry-title, .episode-title").first().text().trim();

    // Stream links container
    const streamEmbeds = [];
    const downloads = [];

    // Phase 2: Find all player options triggering AJAX (.east_player_option or .server_option li div)
    const playerOptions = $(".east_player_option, .server_option li div, .server_option li, .player_option");
    console.log(`[Proxy Scraper] Found list of player option elements: ${playerOptions.length}`);

    // Phase 3: Loop and resolve dynamic AJAX players concurrently where needed
    const ajaxJobs = [];

    playerOptions.each((index, element) => {
      const el = $(element);
      const postId = el.attr("data-post") || el.attr("post") || "";
      const nume = el.attr("data-nume") || el.attr("nume") || "";
      const type = el.attr("data-type") || el.attr("type") || "";
      const serverName = el.text().trim() || el.find("span").text().trim() || `Player Option ${nume}`;

      if (postId && nume && type) {
        ajaxJobs.push((async () => {
          try {
            console.log(`[Proxy AJAX Resolver] triggering admin-ajax.php POST for: ${serverName}`);
            // Phase 3b: Send HTTP POST to theme backend dynamic pipeline endpoint
            const adminAjaxUrl = `${finalDomain}/wp-admin/admin-ajax.php`;
            
            // Replicate proper AJAX Headers context to bypass basic bot shields
            const response = await axios.post(adminAjaxUrl, 
              `action=player_ajax&post=${postId}&nume=${nume}&type=${type}`, 
              {
                headers: {
                  'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                  'X-Requested-With': 'XMLHttpRequest',
                  'Referer': finalUrl,
                  'User-Agent': USER_AGENT
                },
                timeout: 10000
              }
            );

            // Parsing response context (potongan HTML containing <iframe>)
            const responseHtml = response.data;
            const sub$ = cheerio.load(responseHtml);
            const iframe = sub$('iframe').first();
            let src = iframe.attr('src') || '';

            if (src) {
              if (src.startsWith('//')) {
                src = 'https:' + src;
              }
              return {
                serverName,
                iframeUrl: src
              };
            }
          } catch (ajaxError) {
            console.warn(`[Proxy AJAX Resolver] Failed resolving ${serverName}: ${ajaxError.message}`);
          }
          return null;
        })());
      }
    });

    if (ajaxJobs.length > 0) {
      const resolvedOptions = await Promise.all(ajaxJobs);
      const filteredOptions = resolvedOptions.filter(item => item !== null);
      streamEmbeds.push(...filteredOptions);
    }

    // Static Fallback Mirror selectors if dynamic players returned nothing (older themes)
    if (streamEmbeds.length === 0) {
      $(".player-select option, .mirror option").each((index, element) => {
        const option = $(element);
        const name = option.text().trim();
        const val = option.attr("value") || "";
        if (val) {
          streamEmbeds.push({
            serverName: name,
            iframeUrl: val
          });
        }
      });
    }

    // Fallback parser: extract inline frames if still missing
    if (streamEmbeds.length === 0) {
      $("iframe").each((index, element) => {
        let src = $(element).attr("src") || "";
        if (src && !src.includes("ads") && !src.includes("facebook")) {
          if (src.startsWith("//")) src = "https:" + src;
          streamEmbeds.push({
            serverName: `General Iframe Player ${index + 1}`,
            iframeUrl: src
          });
        }
      });
    }

    // Extract download tables (commonly structured in theme layouts)
    $(".download-pdf, .download-link, .dl-links").each((index, group) => {
      const groupEl = $(group);
      const resolution = groupEl.find("strong, b, .resolution").first().text().replace(/:/g, '').trim() || "Download Resolusi";
      const linksList = [];

      groupEl.find("li, a").each((i, linkItem) => {
        const item = $(linkItem);
        const serverName = item.find("strong, b, span").text().replace(/:/g, '').trim() || item.text().trim() || "Download Link";
        const url = item.attr("href") || item.find("a").attr("href") || "";

        if (url && url !== "#") {
          linksList.push({ serverName, url });
        }
      });

      if (linksList.length > 0) {
        downloads.push({
          resolution,
          links: linksList
        });
      }
    });

    res.json({
      success: true,
      domain: finalDomain,
      data: {
        title,
        streamEmbeds,
        downloads
      }
    });
  } catch (error) {
    console.error(`[Proxy API] Error inside /api/episode:`, error);
    res.status(500).json({ success: false, error: error.message });
  }
});

// Default Root status & explanation interface
app.get('/', (req, res) => {
  res.send(`
    <html>
      <head>
        <title>Samehadaku HTML Parser & AJAX Reverse Engineering Proxy API</title>
        <style>
          body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #121214; color: #e1e1e6; line-height: 1.6; padding: 40px; }
          h1 { color: #f25f5f; }
          code { background-color: #202024; padding: 3px 6px; border-radius: 4px; color: #e1e9ff; font-family: monospace; }
          ul { padding-left: 20px; }
          li { margin-bottom: 8px; }
          span.method { font-weight: bold; background: #3b5bdb; padding: 2px 6px; border-radius: 4px; font-size: 0.85em; color: white; display: inline-block; width: 60px; text-align: center; }
          span.method.get { background: #12b886; }
        </style>
      </head>
      <body>
        <h1>Samehadaku Dynamic Decryptor API</h1>
        <p>This proxy scraps raw Samehadaku HTML endpoints and resolves their admin AJAX player payloads into clean streams.</p>
        
        <h2>Available API Endpoints:</h2>
        <ul>
          <li><span class="method get">GET</span> <code>/api/latest?page=1</code> - Latest anime release lists.</li>
          <li><span class="method get">GET</span> <code>/api/search?q={query}&page=1</code> - Anime search functionality.</li>
          <li><span class="method get">GET</span> <code>/api/anime?url={anime_url}</code> - Detailed information, poster, synopsis, types, and episode links.</li>
          <li><span class="method get">GET</span> <code>/api/episode?url={episode_url}</code> - Full dynamic iframe decryption to get streaming links.</li>
        </ul>
        <p>Active domain: <strong>${ACTIVE_BASE_URL}</strong></p>
      </body>
    </html>
  `);
});

// Listen at target machine port
app.listen(PORT, () => {
  console.log(`[Proxy API Server] Running on http://localhost:${PORT}`);
  console.log(`[Proxy API Server] Selected prime domain: ${ACTIVE_BASE_URL}`);
});
