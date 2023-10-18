<html>
<body>
${kcSanitize(msg("identityProviderLinkBodyHtml", identityProviderAlias, realmName, user.username, link, linkExpiration, linkExpirationFormatter(linkExpiration)))?no_esc}
</body>
</html>