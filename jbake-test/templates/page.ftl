<#include "header.ftl">

	<#include "menu.ftl">

	<div class="page-header">
		<h1><#escape x as x?xml>${content.title}</#escape></h1>
	</div>

	<p>${content.body}</p>

	<hr />

	<script type="text/javascript"><!--//--><![CDATA[//><!--
	    var comments_shortname = 'solrcwiki';
	    var comments_identifier = '${content.title}'; // Insert your unique page ID here
	    (function(w, d) {
		    d.write('<div id="comments_thread"><\/div>');
		    var s = d.createElement('script');
		    s.type = 'text/javascript';
		    s.async = true;
		    s.src = 'https://comments.apache.org/show_comments.lua?site=' + comments_shortname + '&page=' + comments_identifier + '&oldschool=true';
		    (d.getElementsByTagName('head')[0] || d.getElementsByTagName('body')[0]).appendChild(s);
	    })(window, document);
	    //--><!]]></script>
	    <noscript>
	    <iframe width="100%" height="500" src="https://comments.apache.org/iframe.lua?site=solrcwiki&amp;page=${content.title}&oldschool=true"></iframe>
	    </noscript>

<#include "footer.ftl">
