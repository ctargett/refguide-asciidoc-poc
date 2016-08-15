<#include "header.ftl">

	<#include "menu.ftl">

	<div class="page-header">
		<h1><#escape x as x?xml>${content.title}</#escape></h1>
	</div>

	<p>${content.body}</p>

	<hr />

		 <div id="comments_thread">
		 </div>
		 <script type="text/javascript" src="https://comments.apache.org/show_comments.lua?site=solrcwiki&style=https://home.apache.org/~ctargett/RefGuidePOC/jekyll/css/comments.css&page=${content.shortname}" async="true">
		 </script>
		 <noscript>
		 <iframe width="100%" height="500" src="https://comments.apache.org/iframe.lua?site=solrcwiki&style=https://home.apache.org/~ctargett/RefGuidePOC/jekyll/css/comments.css&page=${content.shortname}"></iframe>
		 </noscript>
		 
<#include "footer.ftl">
