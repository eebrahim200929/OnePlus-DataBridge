package sa.databridge.oneplus;

interface IFileAccess {
    String[] list(in String path);
    String copyToDownloads(in String sourcePath);
}
