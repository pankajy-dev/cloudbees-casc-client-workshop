/**
 * It copies the given text into the clipboard, synchronously.
 * It temporarily creates a non visible textarea.
 * @param {String} text to copy
 */
export function copyString(text) {
    //make an invisible textarea element containing the text
    var textHolder = document.createElement('textarea');
    textHolder.value = text;
    //Hide the textarea. Shouldn't be perceptible
    textHolder.style.position = 'absolute';
    textHolder.style.left = '-9999px';

    //Put the textarea in the DOM
    document.body.appendChild(textHolder);
    //select the text and copy it to the clipboard
    textHolder.select();
    document.execCommand('copy');

    //remove the textarea from the DOM
    document.body.removeChild(textHolder);
}